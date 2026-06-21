package com.example.createcrashfixcrashfix;

import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformer.Target;
import cpw.mods.modlauncher.api.ITransformerVotingContext;
import cpw.mods.modlauncher.api.TransformerVoteResult;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.File;
import java.net.URL;
import java.net.URLDecoder;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class CreateCrashFixCrashFixTransformer implements ITransformer<ClassNode> {
    // Hardcoded known targets (fallback)
    private static final Set<String> KNOWN_CLASSES = new HashSet<>(Arrays.asList(
            "com.chapple.createcrash.CreateCrash",
            "com.chapple.createcrash.CreateCrasha"
    ));

    // All discovered targets (hardcoded + auto-scanned)
    private static final Set<String> TARGET_CLASSES = new HashSet<>(KNOWN_CLASSES);
    private static final Set<String> TARGET_INTERNALS = new HashSet<>();

    private static final String TARGET_PACKAGE = "com.chapple.createcrash";
    private static final String TARGET_PACKAGE_PATH = "com/chapple/createcrash";

    static {
        // Auto-discover any class in the target package from mod JARs on disk
        discoverTargets();
        // Build internal-name set
        for (String clazz : TARGET_CLASSES) {
            TARGET_INTERNALS.add(clazz.replace('.', '/'));
        }
    }

    private static void discoverTargets() {
        // Strategy 1: Scan mods directories on disk
        scanModsDirectories();

        // Strategy 2: Scan via ClassLoader resources (works in dev env)
        scanClassLoaderResources();
    }

    private static void scanModsDirectories() {
        // Common Forge mods directory locations
        String[] candidates = {"mods", "../mods", "run/mods", "build/mods"};
        for (String dir : candidates) {
            File d = new File(dir);
            if (d.isDirectory()) {
                scanJarsInDirectory(d);
            }
        }
    }

    private static void scanJarsInDirectory(File dir) {
        File[] jars = dir.listFiles((d, name) -> name.endsWith(".jar"));
        if (jars == null) return;
        for (File jarFile : jars) {
            scanJarForClasses(jarFile);
        }
    }

    private static void scanJarForClasses(File jarFile) {
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.startsWith(TARGET_PACKAGE_PATH + "/") && name.endsWith(".class")) {
                    String className = name.replace('/', '.').replace(".class", "");
                    TARGET_CLASSES.add(className);
                }
            }
        } catch (Exception ignored) {
            // Best effort — hardcoded targets still work
        }
    }

    private static void scanClassLoaderResources() {
        try {
            ClassLoader cl = CreateCrashFixCrashFixTransformer.class.getClassLoader();
            if (cl == null) cl = ClassLoader.getSystemClassLoader();

            Enumeration<URL> resources = cl.getResources(TARGET_PACKAGE_PATH);
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                switch (url.getProtocol()) {
                    case "file": {
                        File dir = new File(url.toURI());
                        File[] files = dir.listFiles((d, name) -> name.endsWith(".class"));
                        if (files != null) {
                            for (File f : files) {
                                TARGET_CLASSES.add(TARGET_PACKAGE + "." + f.getName().replace(".class", ""));
                            }
                        }
                        // Also scan for inner/sub directories
                        scanSubdirectories(dir, TARGET_PACKAGE);
                        break;
                    }
                    case "jar": {
                        String path = URLDecoder.decode(url.getPath(), "UTF-8");
                        int sep = path.indexOf('!');
                        if (sep > 0) {
                            String jarPath = path.substring(5, sep); // strip "jar:file:"
                            scanJarForClasses(new File(jarPath));
                        }
                        break;
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static void scanSubdirectories(File dir, String prefix) {
        File[] subDirs = dir.listFiles(File::isDirectory);
        if (subDirs == null) return;
        for (File sub : subDirs) {
            String newPrefix = prefix + "." + sub.getName();
            File[] classFiles = sub.listFiles((d, name) -> name.endsWith(".class"));
            if (classFiles != null) {
                for (File f : classFiles) {
                    TARGET_CLASSES.add(newPrefix + "." + f.getName().replace(".class", ""));
                }
            }
            scanSubdirectories(sub, newPrefix);
        }
    }

    @Override
    public ClassNode transform(ClassNode input, ITransformerVotingContext context) {
        if (!TARGET_INTERNALS.contains(input.name)) {
            return input;
        }

        // Patch EVERY method — constructor, static, instance — all become no-ops.
        // This neutralises not only the intentional crash in <init> but also any
        // method that attempts to rename/repackage the class to evade detection.
        for (MethodNode method : input.methods) {
            method.instructions = createEmptyBody(method);
            method.tryCatchBlocks.clear();
            method.localVariables = null;

            boolean isStatic = (method.access & Opcodes.ACC_STATIC) != 0;
            method.maxStack = 2;
            method.maxLocals = isStatic ? 0 : 1;
        }

        return input;
    }

    /**
     * Build the smallest valid method body for the given method descriptor.
     * - Constructors: {@code super(); return;}
     * - Void methods: {@code return;}
     * - Primitive-return methods: push 0 / 0L / 0.0f / 0.0, then return
     * - Object-return methods: {@code return null;}
     */
    private static InsnList createEmptyBody(MethodNode method) {
        InsnList insns = new InsnList();
        String desc = method.desc;
        char returnType = desc.charAt(desc.indexOf(')') + 1);

        // Constructors <init> must call super()
        if ("<init>".equals(method.name)) {
            insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
            insns.add(new MethodInsnNode(
                    Opcodes.INVOKESPECIAL,
                    "java/lang/Object",
                    "<init>",
                    "()V",
                    false
            ));
        }

        switch (returnType) {
            case 'V':  // void
                insns.add(new InsnNode(Opcodes.RETURN));
                break;
            case 'I': case 'Z': case 'B': case 'S': case 'C':
                insns.add(new InsnNode(Opcodes.ICONST_0));
                insns.add(new InsnNode(Opcodes.IRETURN));
                break;
            case 'J':  // long
                insns.add(new InsnNode(Opcodes.LCONST_0));
                insns.add(new InsnNode(Opcodes.LRETURN));
                break;
            case 'F':  // float
                insns.add(new InsnNode(Opcodes.FCONST_0));
                insns.add(new InsnNode(Opcodes.FRETURN));
                break;
            case 'D':  // double
                insns.add(new InsnNode(Opcodes.DCONST_0));
                insns.add(new InsnNode(Opcodes.DRETURN));
                break;
            default:   // object reference (Ljava/lang/Object;  or  […)
                insns.add(new InsnNode(Opcodes.ACONST_NULL));
                insns.add(new InsnNode(Opcodes.ARETURN));
                break;
        }

        return insns;
    }

    @Override
    public TransformerVoteResult castVote(ITransformerVotingContext context) {
        return TransformerVoteResult.YES;
    }

    @Override
    public Set<Target> targets() {
        Set<Target> result = new HashSet<>();
        for (String clazz : TARGET_CLASSES) {
            result.add(Target.targetClass(clazz));
        }
        return result;
    }
}
