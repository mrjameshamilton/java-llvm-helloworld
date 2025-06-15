package eu.jameshamilton;

import java.lang.foreign.Arena;

import static eu.jameshamilton.llvm.LLVM.LLVMAppendBasicBlock;
import static eu.jameshamilton.llvm.LLVM.LLVMBuildCall2;
import static eu.jameshamilton.llvm.LLVM.LLVMBuildGlobalStringPtr;
import static eu.jameshamilton.llvm.LLVM.LLVMBuildRet;
import static eu.jameshamilton.llvm.LLVM.LLVMCreateBuilder;
import static eu.jameshamilton.llvm.LLVM.LLVMDisposeBuilder;
import static eu.jameshamilton.llvm.LLVM.LLVMPositionBuilderAtEnd;
import static eu.jameshamilton.llvm.LLVM_1.LLVMAddFunction;
import static eu.jameshamilton.llvm.LLVM_1.LLVMConstInt;
import static eu.jameshamilton.llvm.LLVM_1.LLVMDisposeMessage;
import static eu.jameshamilton.llvm.LLVM_1.LLVMDisposeModule;
import static eu.jameshamilton.llvm.LLVM_1.LLVMFunctionType;
import static eu.jameshamilton.llvm.LLVM_1.LLVMInt32Type;
import static eu.jameshamilton.llvm.LLVM_1.LLVMInt8Type;
import static eu.jameshamilton.llvm.LLVM_1.LLVMModuleCreateWithName;
import static eu.jameshamilton.llvm.LLVM_1.LLVMPointerType;
import static eu.jameshamilton.llvm.LLVM_1.LLVMPrintModuleToFile;
import static eu.jameshamilton.llvm.LLVM_1.LLVMPrintModuleToString;
import static java.lang.foreign.MemorySegment.NULL;
import static java.lang.foreign.ValueLayout.ADDRESS;

public class HelloLLVM {
    void main() {
        try (Arena arena = Arena.ofConfined()) {
            var module = LLVMModuleCreateWithName(arena.allocateFrom("hello"));

            // Create main function signature: int main()
            var int32Type = LLVMInt32Type();
            var mainType = LLVMFunctionType(int32Type, NULL, 0, 0);
            var mainFunc = LLVMAddFunction(module, arena.allocateFrom("main"), mainType);

            // Create puts function signature: int puts(char*)
            var putsParamTypes = arena.allocate(ADDRESS, 1);
            var charPtrType = LLVMPointerType(LLVMInt8Type(), 0);
            putsParamTypes.set(ADDRESS, 0, charPtrType);
            var putsType = LLVMFunctionType(int32Type, putsParamTypes, 1, 0);
            var putsFunc = LLVMAddFunction(module, arena.allocateFrom("puts"), putsType);

            // Create basic block for the entry block
            var entry = LLVMAppendBasicBlock(mainFunc, arena.allocateFrom("entry"));
            var builder = LLVMCreateBuilder();
            LLVMPositionBuilderAtEnd(builder, entry);

            // Create global string constant containing "Hello, World!"
            var helloStr = LLVMBuildGlobalStringPtr(builder,
                arena.allocateFrom("Hello, World!"),
                arena.allocateFrom("hello_str"));

            // Create puts function call
            var callArgs = arena.allocate(ADDRESS, 1);
            callArgs.set(ADDRESS, 0, helloStr);
            LLVMBuildCall2(builder, putsType, putsFunc, callArgs, 1, arena.allocateFrom("puts"));

            // Return 0
            LLVMBuildRet(builder, LLVMConstInt(int32Type, 0, 0));

            var llvmIrCharPtr = LLVMPrintModuleToString(module);

            try {
                var boundedPtr = llvmIrCharPtr.reinterpret(8192);
                System.out.println(boundedPtr.getString(0));
            } catch (Exception e) {
                System.err.println("Failed to write LLVM IR: failed to get error message: " + e.getMessage());
            } finally {
                LLVMDisposeMessage(llvmIrCharPtr);
            }

            // Write LLVM IR to file
            var errorMsgPtrPtr = arena.allocate(ADDRESS);
            var printToFileResult = LLVMPrintModuleToFile(module,
                arena.allocateFrom("helloworld.ll"),
                errorMsgPtrPtr);

            if (printToFileResult != 0) {
                var errorMsgPtr = errorMsgPtrPtr.get(ADDRESS, 0);
                if (errorMsgPtr.address() != 0) {
                    try {
                        var boundedErrorPtr = errorMsgPtr.reinterpret(256);
                        System.err.println("Failed to write LLVM IR file: " + boundedErrorPtr.getString(0));
                    } catch (Exception e) {
                        System.err.println("Failed to write LLVM IR file: failed to get error message: " + e.getMessage());
                    } finally {
                        LLVMDisposeMessage(errorMsgPtr);
                    }
                } else {
                    System.err.println("Failed to write LLVM IR file");
                }
            }

            // Clean up LLVM resources
            LLVMDisposeBuilder(builder);
            LLVMDisposeModule(module);
        }
    }
}