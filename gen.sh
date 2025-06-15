#!/bin/bash

jextract -l LLVM-20 -I /usr/include/llvm-c-20 \
                    -I /usr/include/llvm-20 \
                    -t eu.jameshamilton.llvm \
                    --output src/main/java \
                    --header-class-name LLVM \
                    /usr/include/llvm-c-20/llvm-c/Core.h
