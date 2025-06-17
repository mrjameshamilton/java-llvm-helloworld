#!/bin/bash

jextract -l LLVM-20 -I /usr/include/llvm-c-20 \
                    -I /usr/include/llvm-20 \
                    -t eu.jameshamilton.llvm \
                    --output src/main/java \
                    --header-class-name LLVM \
                    /usr/include/llvm-c-20/llvm-c/Core.h \
                    /usr/include/llvm-c-20/llvm-c/Support.h \
                    /usr/include/llvm-c-20/llvm-c/ExecutionEngine.h \
                    /usr/include/llvm-c-20/llvm-c/Target.h \
                    /usr/include/llvm-c-20/llvm-c/TargetMachine.h
