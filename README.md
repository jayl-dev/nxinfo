# nxinfo

A simple android app that parses switch nsp, xci files and display the files' meta data.

From time to time, I need to figure out the build ids of a switch game so that I can download cheats for them. (https://www.cheatslips.com/)
I have found that Ryujinx emulator https://ryujinx.app/ and NxFileViewer https://github.com/Myster-Tee/NxFileViewer can parse and display the build ids but neither of them are available on android.

Often times I play switch games on my android tablets and I got tired of copying the switch files over to a computer just to figure out the build id, so I created this app for myself. 

# Credits

The app uses modified c++ codes from https://github.com/jakcron/nstool and various open sourced libraries to parse the switch files.

https://github.com/jakcron/libmbedtls.git

https://github.com/jakcron/libfmt.git

https://github.com/jakcron/libtoolchain.git

https://github.com/jakcron/liblz4.git

https://github.com/jakcron/libpietendo.git

Most of the android UI codes are generated because AI can write much faster than I can :) 

If you have similar needs for android, feel free to take the codes and/or the apk and do whatever you want with it.