package eu.jameshamilton;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;

import static eu.jameshamilton.llvm.LLVM.LLVMAppendBasicBlock;
import static eu.jameshamilton.llvm.LLVM.LLVMBuildCall2;
import static eu.jameshamilton.llvm.LLVM.LLVMBuildGlobalStringPtr;
import static eu.jameshamilton.llvm.LLVM.LLVMBuildRet;
import static eu.jameshamilton.llvm.LLVM.LLVMCreateBuilder;
import static eu.jameshamilton.llvm.LLVM.LLVMCreateJITCompilerForModule;
import static eu.jameshamilton.llvm.LLVM.LLVMDisposeBuilder;
import static eu.jameshamilton.llvm.LLVM.LLVMGetDefaultTargetTriple;
import static eu.jameshamilton.llvm.LLVM.LLVMGetPointerToGlobal;
import static eu.jameshamilton.llvm.LLVM.LLVMInitializeX86AsmParser;
import static eu.jameshamilton.llvm.LLVM.LLVMInitializeX86AsmPrinter;
import static eu.jameshamilton.llvm.LLVM.LLVMInitializeX86Target;
import static eu.jameshamilton.llvm.LLVM.LLVMInitializeX86TargetInfo;
import static eu.jameshamilton.llvm.LLVM.LLVMInitializeX86TargetMC;
import static eu.jameshamilton.llvm.LLVM.LLVMLinkInMCJIT;
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
import static java.lang.foreign.ValueLayout.JAVA_INT;

public class HelloLLVM {
    void main() {
        try (Arena arena = Arena.ofConfined()) {
            // Initialize LLVM
            LLVMLinkInMCJIT();

            var targetTriple = LLVMGetDefaultTargetTriple().getString(0);
            if (targetTriple.startsWith("x86")) {
                LLVMInitializeX86Target();
                LLVMInitializeX86TargetInfo();
                LLVMInitializeX86TargetMC();
                LLVMInitializeX86AsmPrinter();
                LLVMInitializeX86AsmParser();
            } else {
                throw new RuntimeException("Unsupported target: " + targetTriple);
            }

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

            // Create JIT execution engine
            var jitCompiler = arena.allocate(ADDRESS);
            var jitErrorMsgPtrPtr = arena.allocate(ADDRESS);
            int createJitResult = LLVMCreateJITCompilerForModule(jitCompiler, module, 2, jitErrorMsgPtrPtr);

            if (createJitResult != 0) {
                var errorMsgPtr = jitErrorMsgPtrPtr.get(ADDRESS, 0);
                if (errorMsgPtr.address() != 0) {
                    try {
                        var boundedErrorPtr = errorMsgPtr.reinterpret(256);
                        System.err.println("Failed to create JIT: " + boundedErrorPtr.getString(0));
                    } catch (Exception e) {
                        System.err.println("Failed to create JIT: failed to get error message: " + e.getMessage());
                    } finally {
                        LLVMDisposeMessage(errorMsgPtr);
                    }
                } else {
                    System.err.println("JIT creation failed");
                }

                // Clean up LLVM resources
                LLVMDisposeBuilder(builder);
                LLVMDisposeModule(module);
                return;
            }

            var executionEngine = jitCompiler.get(ADDRESS, 0);

            // Create method handle to the int main() function that
            // we just created and compiled.
            var functionHandle = Linker.nativeLinker().downcallHandle(
                LLVMGetPointerToGlobal(executionEngine, mainFunc),
                FunctionDescriptor.of(/* returnType = */ JAVA_INT)
            );

            // Execute the main function via the method handle.
            try {
                int result = (int) functionHandle.invoke();
                System.out.println("main() returned: " + result);
            } catch (Throwable e) {
                System.err.println("Error calling JIT function: " + e.getMessage());
            }

            // Clean up LLVM resources
            LLVMDisposeBuilder(builder);
            LLVMDisposeModule(module);
        }
    }
}