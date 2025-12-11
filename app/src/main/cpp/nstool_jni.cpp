#include <jni.h>
#include <string>
#include <cstring>
#include <vector>
#include <sstream>
#include <iomanip>
#include <memory>
#include <algorithm>
#include <android/log.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/stat.h>

#include <tc/io/IStream.h>
#include <tc/io/Path.h>
#include <tc/io/VirtualFileSystem.h>
#include <tc/string.h>
#include <tc/io/IOException.h>
#include <tc/NotSupportedException.h>

#include <pietendo/hac/define/nacp.h>
#include <pietendo/hac/ContentArchiveUtil.h>

#include "Settings.h"
#include "GameCardProcess.h"
#include "PfsProcess.h"
#include "NcaProcess.h"
#include "NsoProcess.h"
#include "EsTikProcess.h"
#include "KeyBag.h"

#define LOG_TAG "nstool_jni"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

#define MAX_ICON_SIZE (2 * 1024 * 1024)
#define MAX_CONTROL_NCA_SIZE (50 * 1024 * 1024)
#define MAX_NACP_SIZE (32 * 1024)
#define MAX_NSO_SIZE (50 * 1024 * 1024)

std::string jstringToString(JNIEnv* env, jstring jStr) {
    if (!jStr) return "";
    const char* chars = env->GetStringUTFChars(jStr, nullptr);
    std::string ret(chars);
    env->ReleaseStringUTFChars(jStr, chars);
    return ret;
}

// Helper struct for NCA information
struct NcaInfo {
    std::string path;
    int64_t size;
    std::shared_ptr<tc::io::IFileSystem> parentFs;
};

// Recursive helper function to find NCAs within a filesystem
void findNcaFilesRecursive(
    std::shared_ptr<tc::io::IFileSystem> fs,
    const std::string& currentPath,
    std::vector<NcaInfo>& ncaList,
    nstool::KeyBag& keyBag,
    const nstool::CliOutputMode& cliOutputMode,
    bool verify
) {
    LOGI("  Searching for NCAs in path: %s", currentPath.c_str());
    tc::io::sDirectoryListing listing;
    fs->getDirectoryListing(tc::io::Path(currentPath), listing);

    for (const auto &dirName: listing.dir_list) {
        std::string nestedPath = currentPath + "/" + dirName;
        findNcaFilesRecursive(fs, nestedPath, ncaList, keyBag, cliOutputMode,
                              verify); // Recursive call for directories
    }

    for (const auto &fileName: listing.file_list) {
        std::string fullPath = currentPath + "/" + fileName;
        if (fileName.find(".nca") != std::string::npos) {
            try {
                std::shared_ptr<tc::io::IStream> sizeStream;
                fs->openFile(tc::io::Path(fullPath), tc::io::FileMode::Open,
                             tc::io::FileAccess::Read, sizeStream);
                int64_t size = sizeStream->length();
                ncaList.push_back({fullPath, size, fs});
                LOGI("    Found NCA: %s (Size: %lld bytes)", fullPath.c_str(), (long long) size);
            } catch (const std::exception &e) {
                LOGW("    Could not get size for NCA %s: %s", fullPath.c_str(), e.what());
            }
        } else {
            // If not an NCA, try to open as a nested PFS/HFS0
            try {
                std::shared_ptr<tc::io::IStream> nestedStream;
                fs->openFile(tc::io::Path(fullPath), tc::io::FileMode::Open,
                             tc::io::FileAccess::Read, nestedStream);

                nstool::PfsProcess nestedPfsProc;
                nestedPfsProc.setInputFile(nestedStream);
                nestedPfsProc.setCliOutputMode(cliOutputMode);
                nestedPfsProc.setVerifyMode(verify);
                nestedPfsProc.process(); // Try to process as PFS/HFS0

                // If successful, this is a nested filesystem, recurse into it
                LOGI("    Found nested HFS0/PFS0: %s. Recursing...", fullPath.c_str());
                std::shared_ptr<tc::io::IFileSystem> nestedFs = nestedPfsProc.getFileSystem();
                findNcaFilesRecursive(nestedFs, "/", ncaList, keyBag, cliOutputMode,
                                      verify); // Recurse into nested FS
            } catch (const tc::Exception &e) {
                // Not a PFS/HFS0, or failed to process, ignore.
                // LOGV("    File %s is not a nested PFS/HFS0 or failed to process: %s", fullPath.c_str(), e.what());
            } catch (const std::bad_alloc &e) {
                LOGE("    Out of memory processing nested FS %s: %s", fullPath.c_str(), e.what());
            } catch (const std::exception &e) {
                LOGW("    Exception processing nested FS %s: %s", fullPath.c_str(), e.what());
            }
        }
    }
}
class FdStream : public tc::io::IStream
{
public:
    FdStream(int fd) : mFd(-1), mCanRead(false), mCanWrite(false), mCanSeek(false)
    {
        if (fd < 0) {
            throw tc::io::IOException("FdStream: Invalid file descriptor");
        }

        mFd = dup(fd);
        if (mFd < 0) {
            throw tc::io::IOException("FdStream: Failed to duplicate file descriptor");
        }

        mCanRead = true;
        mCanWrite = false;
        mCanSeek = (lseek(mFd, 0, SEEK_CUR) != -1);
    }

    ~FdStream() {
        dispose();
    }

    bool canRead() const override { return mCanRead; }
    bool canWrite() const override { return mCanWrite; }
    bool canSeek() const override { return mCanSeek; }

    int64_t length() override {
        if (!mCanSeek) return 0;
        struct stat st;
        if (fstat(mFd, &st) == 0) {
            return st.st_size;
        }
        return 0;
    }

    int64_t position() override {
        if (!mCanSeek) return 0;
        return lseek(mFd, 0, SEEK_CUR);
    }

    size_t read(byte_t* ptr, size_t count) override {
        if (!mCanRead) throw tc::NotSupportedException("Stream does not support reading");
        ssize_t res = ::read(mFd, ptr, count);
        if (res < 0) {
            throw tc::io::IOException("FdStream: Read failed");
        }
        return (size_t)res;
    }

    size_t write(const byte_t* ptr, size_t count) override {
        throw tc::NotSupportedException("Stream does not support writing");
    }

    int64_t seek(int64_t offset, tc::io::SeekOrigin origin) override {
        if (!mCanSeek) throw tc::NotSupportedException("Stream does not support seeking");
        
        int whence = SEEK_SET;
        switch (origin) {
            case tc::io::SeekOrigin::Begin: whence = SEEK_SET; break;
            case tc::io::SeekOrigin::Current: whence = SEEK_CUR; break;
            case tc::io::SeekOrigin::End: whence = SEEK_END; break;
        }

        off64_t res = lseek64(mFd, offset, whence);
        if (res == (off64_t)-1) {
            throw tc::io::IOException("FdStream: Seek failed");
        }
        return res;
    }

    void setLength(int64_t length) override {
        throw tc::NotSupportedException("Stream does not support setting length");
    }

    void flush() override {
    }

    void dispose() override {
        if (mFd >= 0) {
            close(mFd);
            mFd = -1;
        }
    }

private:
    int mFd;
    bool mCanRead;
    bool mCanWrite;
    bool mCanSeek;
};



extern "C" JNIEXPORT jobject JNICALL
Java_com_jl_nxinfo_SwitchRomParser_parseRomNative(
        JNIEnv* env,
        jobject,
        jint fd,
        jstring fileName,
        jstring keysFile) {

    std::string sFileName = jstringToString(env, fileName);
    std::string sKeysFile = jstringToString(env, keysFile);

    std::string title = "Unknown";
    std::string version = "Unknown";
    std::string titleId = "Unknown";
    std::string sdkVersion = "Unknown";
    std::string buildId = "Unknown";
    std::string fileType = "Unknown";
    std::string decryptionStatusName = "NOT_ATTEMPTED";
    std::vector<byte_t> iconData;

    try {
        nstool::KeyBag keyBag;
        if (!sKeysFile.empty()) {
            try {
                keyBag = nstool::KeyBagInitializer(
                        false,
                        tc::Optional<tc::io::Path>(sKeysFile),
                        tc::Optional<tc::io::Path>(),
                        std::vector<tc::io::Path>(),
                        tc::Optional<tc::io::Path>()
                );
                LOGI("Keys loaded successfully");
                size_t ncaKeyCount = 0;
                for (const auto& keyMap : keyBag.nca_key_area_encryption_key) {
                    ncaKeyCount += keyMap.size();
                }
                LOGI("  NCA Key Area Keys: %zu", ncaKeyCount);
                LOGI("  Title Keys: %zu", keyBag.external_content_keys.size());
                decryptionStatusName = "SUCCESS";
            } catch (const std::exception& e) {
                LOGE("Failed to load keys: %s", e.what());
                decryptionStatusName = "MISSING_KEYS";
            }
        } else {
            decryptionStatusName = "NO_KEYS_FILE";
        }

        std::shared_ptr<tc::io::IStream> fileStream = std::make_shared<FdStream>(fd);

        bool isXci = false;
        bool isNsp = false;
        size_t extPos = sFileName.find_last_of('.');
        if (extPos != std::string::npos) {
            std::string ext = sFileName.substr(extPos + 1);
            for (auto& c : ext) c = tolower(c);
            if (ext == "xci" || ext == "xcz") isXci = true;
            else if (ext == "nsp" || ext == "nsz") isNsp = true;
        }

        std::shared_ptr<tc::io::IFileSystem> fs = nullptr;

        LOGI("Input file: %s", sFileName.c_str());
        LOGI("Input keys file: %s", sKeysFile.c_str());

        if (isXci) {
            fileType = "XCI";
            LOGI("Detected file type: XCI. Processing XCI file...");

            nstool::GameCardProcess obj;
            obj.setInputFile(fileStream);
            obj.setKeyCfg(keyBag);
            obj.setCliOutputMode(nstool::CliOutputMode(false, false, false, false));
            LOGI("Calling GameCardProcess.process() for XCI file...");
            obj.process();
            LOGI("GameCardProcess.process() completed for XCI file.");

            const auto& hdr = obj.getHeader();
            LOGI("=== XCI Header Information ===");
            LOGI("ROM Size Type: 0x%02x", hdr.getRomSizeType());
            LOGI("Package ID: 0x%016llx", (unsigned long long)hdr.getPackageId());
            LOGI("Valid Data End Page: 0x%x", hdr.getValidDataEndPage());
            LOGI("PartitionFS Address: 0x%llx", (unsigned long long)hdr.getPartitionFsAddress());
            LOGI("PartitionFS Size: 0x%llx", (unsigned long long)hdr.getPartitionFsSize());
            LOGI("Key Area Encryption Key Index: %d", hdr.getKekIndex());
            LOGI("Compatibility Type: 0x%02x", hdr.getCompatibilityType());
            LOGI("Card Header Version: %d", hdr.getCardHeaderVersion());
            LOGI("Firmware Version: 0x%llx", (unsigned long long)hdr.getFwVersion());
            LOGI("==============================");

            fs = obj.getFileSystem();
            LOGI("FileSystem obtained from GameCardProcess for XCI file.");

        } else if (isNsp) {
            fileType = "NSP";
            LOGI("Detected file type: NSP. Processing NSP file...");

            nstool::PfsProcess obj;
            obj.setInputFile(fileStream);
            obj.setCliOutputMode(nstool::CliOutputMode(false, false, false, false));
            LOGI("Calling PfsProcess.process() for NSP file...");
            obj.process();
            LOGI("PfsProcess.process() completed for NSP file.");

            const auto& pfsHdr = obj.getPfsHeader();
            LOGI("=== NSP/PFS Header Information ===");
            LOGI("Format Type: %s", pfsHdr.getFsType() == pie::hac::PartitionFsHeader::TYPE_PFS0 ? "PFS0" : "HFS0");
            LOGI("File Count: %zu", pfsHdr.getFileList().size());
            LOGI("File List:");
            for (const auto& file : pfsHdr.getFileList()) {
                LOGI("  - %s (offset: 0x%llx, size: 0x%llx)", file.name.c_str(), (unsigned long long)file.offset, (unsigned long long)file.size);
            }
            LOGI("==================================");

            fs = obj.getFileSystem();

            if (fs != nullptr) {
                tc::io::sDirectoryListing tikList;
                fs->getDirectoryListing(tc::io::Path("/"), tikList);

                for (const auto& fileName : tikList.file_list) {
                    if (fileName.find(".tik") != std::string::npos) {
                        try {
                            LOGI("Processing ticket file: %s", fileName.c_str());
                            std::shared_ptr<tc::io::IStream> tikStream;
                            fs->openFile(tc::io::Path("/" + fileName), tc::io::FileMode::Open, tc::io::FileAccess::Read, tikStream);

                            nstool::EsTikProcess tikProc;
                            tikProc.setInputFile(tikStream);
                            tikProc.setKeyCfg(keyBag);
                            tikProc.setCliOutputMode(nstool::CliOutputMode(false, false, false, false));
                            tikProc.process();

                            const auto& tik = tikProc.getTicket();
                            const auto& tikBody = tik.getBody();

                            nstool::KeyBag::rights_id_t rightsId;
                            memcpy(rightsId.data(), tikBody.getRightsId(), 16);

                            nstool::KeyBag::aes128_key_t titleKey;
                            memcpy(titleKey.data(), tikBody.getEncTitleKey(), 16);

                            keyBag.external_enc_content_keys[rightsId] = titleKey;

                            LOGI("  Extracted titlekey from ticket (will be decrypted when needed)");
                        } catch (const std::exception& e) {
                            LOGW("Failed to process ticket %s: %s", fileName.c_str(), e.what());
                        }
                    }
                }

                LOGI("Total title keys available: %zu", keyBag.external_content_keys.size() + keyBag.external_enc_content_keys.size());
            }
        }

        if (fs != nullptr) {
            LOGI("=== Processing NCA Files ===");

            std::vector<NcaInfo> ncaList;
            LOGI("  Starting recursive NCA search from root filesystem.");
            // Assuming mCliOutputMode from NcaProcess/PfsProcess, but here we don't have it.
            // Using a default CliOutputMode for now, adjust if needed.
            nstool::CliOutputMode defaultCliOutputMode(false, false, false, false);
            bool defaultVerify = false; // Assuming verification is not critical for discovery

            findNcaFilesRecursive(fs, "/", ncaList, keyBag, defaultCliOutputMode, defaultVerify);

            std::sort(ncaList.begin(), ncaList.end(), [](const NcaInfo& a, const NcaInfo& b) {
                return a.size < b.size;
            });

            LOGI("Found %zu NCA files, sorted by size (processing smallest first)", ncaList.size());
            LOGI("--- Starting NCA processing loop ---");

            int ncaCount = 0;
            for (const auto& ncaInfo : ncaList) {
                const std::string& ncaPath = ncaInfo.path;
                int64_t ncaSize = ncaInfo.size;

                LOGI("Processing NCA: %s (Size: %lld MB)", ncaPath.c_str(), (long long)(ncaSize / (1024 * 1024)));



                try {
                    LOGI("  Opening NCA stream for %s from its parent filesystem.", ncaPath.c_str());
                    std::shared_ptr<tc::io::IStream> ncaStream;
                    ncaInfo.parentFs->openFile(tc::io::Path(ncaPath), tc::io::FileMode::Open, tc::io::FileAccess::Read, ncaStream);

                    bool isLikelyControl = (ncaSize < MAX_CONTROL_NCA_SIZE);
                    LOGI("  Instantiating NcaProcess for %s (likely %s)",
                         ncaPath.c_str(),
                         isLikelyControl ? "Control" : "Program");

                    nstool::NcaProcess ncaObj;
                    ncaObj.setInputFile(ncaStream);
                    ncaObj.setKeyCfg(keyBag);

                    ncaObj.setCliOutputMode(nstool::CliOutputMode(false, false, false, false));
                    ncaObj.setShowFsTree(false);
                    ncaObj.setFsRootLabel(ncaPath);

                    LOGI("  Calling ncaObj.process() for %s", ncaPath.c_str());
                    ncaObj.process();
                    LOGI("  ncaObj.process() completed for %s", ncaPath.c_str());

                    const auto& ncaHdr = ncaObj.getHeader();
                    LOGI("  NCA Header Content Type: %d, Program ID: %016llx",
                         ncaHdr.getContentType(), (unsigned long long)ncaHdr.getProgramId());
                    LOGI("  SDK Version: %s", pie::hac::ContentArchiveUtil::getSdkAddonVersionAsString(ncaHdr.getSdkAddonVersion()).c_str());
                    LOGI("  Key Generation: %d", ncaHdr.getKeyGeneration());
                    LOGI("  Content Size: 0x%llx", (unsigned long long)ncaHdr.getContentSize());
                    LOGI("  Distribution Type: %d", ncaHdr.getDistributionType());
                    LOGI("  Has Rights ID: %s", ncaHdr.hasRightsId() ? "YES" : "NO");
                    LOGI("  Partition Entries: %zu", ncaHdr.getPartitionEntryList().size());

                    if (titleId == "Unknown") {
                        char buf[17];
                        snprintf(buf, sizeof(buf), "%016llx", (unsigned long long)ncaHdr.getProgramId());
                        titleId = std::string(buf);
                        for (auto& c : titleId) c = toupper(c);
                    }

                    if (sdkVersion == "Unknown") {
                        sdkVersion = pie::hac::ContentArchiveUtil::getSdkAddonVersionAsString(ncaHdr.getSdkAddonVersion());
                    }

                    if (title == "Unknown" && ncaHdr.getContentType() == pie::hac::nca::ContentType_Control) {
                        LOGI("  --- Identified CONTROL NCA %s ---", ncaPath.c_str());
                        LOGI("  Found Control NCA! Attempting to extract title and version...");

                        try {
                            auto ncaFs = ncaObj.getFileSystem();
                            if (ncaFs) {
                                LOGI("  Control NCA filesystem mounted successfully for %s", ncaPath.c_str());

                                std::shared_ptr<tc::io::IStream> nacpStream;
                                LOGI("  Opening /0/control.nacp for %s", ncaPath.c_str());
                                ncaFs->openFile(tc::io::Path("/0/control.nacp"), tc::io::FileMode::Open, tc::io::FileAccess::Read, nacpStream);

                                size_t nacpSize = nacpStream->length();
                                LOGI("  NACP file size: %zu bytes for %s", nacpSize, ncaPath.c_str());

                                if (nacpSize >= sizeof(pie::hac::sApplicationControlProperty)) {
                                    try {
                                        LOGI("  Allocating %zu bytes for NACP data for %s", nacpSize, ncaPath.c_str());
                                        std::vector<byte_t> nacpData(nacpSize);
                                        nacpStream->read(nacpData.data(), nacpSize);
                                        LOGI("  NACP data read for %s", ncaPath.c_str());

                                        const pie::hac::sApplicationControlProperty* nacp =
                                            reinterpret_cast<const pie::hac::sApplicationControlProperty*>(nacpData.data());

                                        if (nacp->title[0].name.size() > 0) {
                                            title = nacp->title[0].name.decode();
                                        } else if (nacp->title[1].name.size() > 0) {
                                            title = nacp->title[1].name.decode();
                                        } else {
                                            for (size_t i = 0; i < pie::hac::nacp::kMaxLanguageCount; i++) {
                                                if (nacp->title[i].name.size() > 0) {
                                                    title = nacp->title[i].name.decode();
                                                    break;
                                                }
                                            }
                                        }

                                        if (nacp->display_version.size() > 0) {
                                            version = nacp->display_version.decode();
                                        }
                                    } catch (const std::bad_alloc& e) {
                                        LOGE("  Out of memory reading NACP data for %s: %s", ncaPath.c_str(), e.what());
                                    }
                                }

                                LOGI("  Title: %s, Version: %s for %s", title.c_str(), version.c_str(), ncaPath.c_str());

                                std::vector<std::string> iconNames = {
                                    "/0/icon_AmericanEnglish.dat",
                                    "/0/icon_BritishEnglish.dat",
                                    "/0/icon_CanadianFrench.dat",
                                    "/0/icon_Japanese.dat",
                                    "/0/icon_French.dat",
                                    "/0/icon_German.dat",
                                    "/0/icon_Spanish.dat",
                                    "/0/icon_Italian.dat",
                                    "/0/icon_Dutch.dat",
                                    "/0/icon_Portuguese.dat",
                                    "/0/icon_Russian.dat",
                                    "/0/icon_Korean.dat",
                                    "/0/icon_TraditionalChinese.dat",
                                    "/0/icon_SimplifiedChinese.dat"
                                };

                                for (const auto& iconName : iconNames) {
                                    try {
                                        LOGI("  Attempting to open icon file %s from %s", iconName.c_str(), ncaPath.c_str());
                                        std::shared_ptr<tc::io::IStream> iconStream;
                                        ncaFs->openFile(tc::io::Path(iconName), tc::io::FileMode::Open, tc::io::FileAccess::Read, iconStream);

                                        size_t iconSize = iconStream->length();
                                        LOGI("  Icon file %s size: %zu bytes from %s", iconName.c_str(), iconSize, ncaPath.c_str());

                                        try {
                                            LOGI("  Allocating %zu bytes for icon data for %s", iconSize, ncaPath.c_str());
                                            iconData.resize(iconSize);
                                            iconStream->read(iconData.data(), iconSize);
                                            LOGI("  Icon data read for %s", ncaPath.c_str());

                                            LOGI("  Icon extracted: %s (%zu bytes) from %s", iconName.c_str(), iconSize, ncaPath.c_str());
                                            break;
                                        } catch (const std::bad_alloc& e) {
                                            LOGE("  Out of memory reading icon data for %s: %s", ncaPath.c_str(), e.what());
                                            iconData.clear();
                                        }
                                    } catch (const std::exception& e) {
                                        LOGW("  Could not open/read icon %s from %s: %s", iconName.c_str(), ncaPath.c_str(), e.what());
                                    }
                                }

                                if (iconData.empty()) {
                                    LOGW("  No icon found in Control NCA for %s", ncaPath.c_str());
                                }
                            } else {
                                LOGW("  Control NCA filesystem is NULL for %s", ncaPath.c_str());
                            }
                        } catch (const std::exception& e) {
                            LOGW("  Failed to extract title/icon from Control NCA %s: %s", ncaPath.c_str(), e.what());
                        }
                    }

                    if (buildId == "Unknown" && ncaHdr.getContentType() == pie::hac::nca::ContentType_Program) {
                        LOGI("  --- Identified PROGRAM NCA %s ---", ncaPath.c_str());
                        LOGI("  Found Program NCA %s! Attempting to extract Build ID...", ncaPath.c_str());

                            try {
                                LOGI("  Getting filesystem for Program NCA %s", ncaPath.c_str());
                                auto ncaFs = ncaObj.getFileSystem();
                                if (ncaFs) {
                                    LOGI("  Program NCA filesystem mounted for %s, looking for NSO files...", ncaPath.c_str());

                                    std::vector<std::string> possibleNames = {"/0/main", "/0/main.nso"};

                                    for (const auto& fileName : possibleNames) {
                                        try {
                                            LOGI("  Attempting to open NSO file %s from %s", fileName.c_str(), ncaPath.c_str());
                                            std::shared_ptr<tc::io::IStream> nsoStream;
                                            ncaFs->openFile(tc::io::Path(fileName), tc::io::FileMode::Open, tc::io::FileAccess::Read, nsoStream);

                                            int64_t nsoSize = nsoStream->length();
                                            LOGI("  NSO file %s size: %lld bytes from %s", fileName.c_str(), (long long)nsoSize, ncaPath.c_str());

                                            LOGI("  Instantiating NsoProcess for %s (header-only)", fileName.c_str());
                                            nstool::NsoProcess nsoProc;
                                            nsoProc.setInputFile(nsoStream);
                                            nsoProc.setCliOutputMode(nstool::CliOutputMode(false, false, false, false));
                                            nsoProc.setExtractHeaderOnly(true); // <-- Added this line
                                            LOGI("  Calling nsoProc.process() for %s", fileName.c_str());
                                            nsoProc.process();
                                            LOGI("  nsoProc.process() completed for %s", fileName.c_str());

                                            const auto& nsoHdr = nsoProc.getHeader();
                                            const auto& moduleId = nsoHdr.getModuleId();

                                            std::stringstream ss;
                                            for (size_t i = 0; i < moduleId.size(); i++) {
                                                ss << std::hex << std::setw(2) << std::setfill('0') << (int)moduleId[i];
                                            }
                                            buildId = ss.str();
                                            for (auto& c : buildId) c = toupper(c);

                                            LOGI("  Build ID extracted: %s from %s", buildId.c_str(), fileName.c_str());
                                            break;
                                        } catch (const std::bad_alloc& e) {
                                            LOGE("  Out of memory while extracting Build ID from NSO %s for NCA %s: %s", fileName.c_str(), ncaPath.c_str(), e.what());
                                            if (decryptionStatusName == "SUCCESS") decryptionStatusName = "DECRYPTION_ERROR";
                                        } catch (const std::exception& e) {
                                            LOGW("  Could not read NSO %s for NCA %s: %s", fileName.c_str(), ncaPath.c_str(), e.what());
                                        }
                                    }
                                } else {
                                    LOGW("  Program NCA filesystem is NULL for %s - decryption may have failed", ncaPath.c_str());
                                }
                            } catch (const std::exception& e) {
                                LOGW("  Failed to extract Build ID from Program NCA %s: %s", ncaPath.c_str(), e.what());
                            }



                    LOGI("(All essential metadata extracted, processed %d NCAs for %s)", ncaCount + 1, ncaPath.c_str());
                    }

                    if (ncaCount >= 15) {
                        LOGI("(Reached maximum of 15 NCAs processed for %s)", ncaPath.c_str());
                    }

                } catch (const std::bad_alloc& e) {
                    LOGE("Out of memory while processing NCA %s: %s", ncaPath.c_str(), e.what());
                    if (decryptionStatusName == "SUCCESS") decryptionStatusName = "DECRYPTION_ERROR";
                    break;
                } catch (const std::exception& e) {
                    LOGE("Failed to process NCA %s: %s", ncaPath.c_str(), e.what());
                    if (decryptionStatusName == "SUCCESS") decryptionStatusName = "DECRYPTION_ERROR";
                } catch (...) {
                    LOGE("Unknown error while processing NCA %s", ncaPath.c_str());
                    if (decryptionStatusName == "SUCCESS") decryptionStatusName = "DECRYPTION_ERROR";
                }
            }
            LOGI("--- Finished NCA processing loop ---");
            LOGI("Final extracted metadata: Title=%s, Version=%s, TitleId=%s, SdkVersion=%s, BuildId=%s, FileType=%s, IconDataSize=%zu",
                 title.c_str(), version.c_str(), titleId.c_str(), sdkVersion.c_str(), buildId.c_str(), fileType.c_str(), iconData.size());
            LOGI("=============================");
        }

    } catch (const std::bad_alloc& e) {
        LOGE("Out of memory in parseRomNative: %s", e.what());
        if (decryptionStatusName == "NOT_ATTEMPTED") decryptionStatusName = "DECRYPTION_ERROR";
    } catch (const std::exception& e) {
        LOGE("Exception in parseRomNative: %s", e.what());
        if (decryptionStatusName == "NOT_ATTEMPTED") decryptionStatusName = "DECRYPTION_ERROR";
    } catch (...) {
        LOGE("Unknown exception in parseRomNative");
        if (decryptionStatusName == "NOT_ATTEMPTED") decryptionStatusName = "DECRYPTION_ERROR";
    }

    jclass resultClass = env->FindClass("com/jl/nxinfo/SwitchRomInfo");
    jmethodID constructor = env->GetMethodID(resultClass, "<init>", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[B)V");

    jbyteArray jIconData = nullptr;
    if (!iconData.empty()) {
        jIconData = env->NewByteArray(iconData.size());
        env->SetByteArrayRegion(jIconData, 0, iconData.size(), reinterpret_cast<const jbyte*>(iconData.data()));
    }

    return env->NewObject(resultClass, constructor,
            env->NewStringUTF(title.c_str()),
            env->NewStringUTF(version.c_str()),
            env->NewStringUTF(titleId.c_str()),
            env->NewStringUTF(sdkVersion.c_str()),
            env->NewStringUTF(buildId.c_str()),
            env->NewStringUTF(fileType.c_str()),
            jIconData);
}

