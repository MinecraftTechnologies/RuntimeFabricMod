
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.HashMap;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

public class GenerateHeaders {

    private static class ClassFile implements Comparable<ClassFile> {
        private String name;
        private String superClass;
        private String[] interfaces;
        private byte[] classData;
        private String[] cpStrings;
        private short[] cpClasses;
        private int priority = 0;
        private static final int HEAD = -889275714;
        private static final byte CONSTANT_Utf8 = 1;
        private static final byte CONSTANT_Integer = 3;
        private static final byte CONSTANT_Float = 4;
        private static final byte CONSTANT_Long = 5;
        private static final byte CONSTANT_Double = 6;
        private static final byte CONSTANT_Class = 7;
        private static final byte CONSTANT_String = 8;
        private static final byte CONSTANT_FieldRef = 9;
        private static final byte CONSTANT_MethodRef = 10;
        private static final byte CONSTANT_InterfaceMethodRef = 11;
        private static final byte CONSTANT_NameAndType = 12;
        private static final byte CONSTANT_MethodHandle = 15;
        private static final byte CONSTANT_MethodType = 16;
        private static final byte CONSTANT_InvokeDynamic = 18;

        public ClassFile(byte[] classData) {
            this.classData = classData;
            this.parse(ByteBuffer.wrap(classData));
        }

        private void parse(ByteBuffer buf) {
            if (buf.order(ByteOrder.BIG_ENDIAN).getInt() != HEAD) {
                throw new RuntimeException("Not a class file");
            }
            buf.getChar();
            buf.getChar();
            int cpSize = buf.getChar();
            this.cpStrings = new String[cpSize];
            this.cpClasses = new short[cpSize];

            for (int i = 1; i < cpSize; i++) {
                byte tag = buf.get();
                switch (tag) {
                    case CONSTANT_Utf8:
                        this.cpStrings[i] = this.decodeString(buf);
                        break;
                    default:
                        throw new RuntimeException("Failed to read constant pool because of type " + tag);
                    case CONSTANT_Integer:
                        buf.getInt();
                        break;
                    case CONSTANT_Float:
                        buf.getFloat();
                        break;
                    case CONSTANT_Long:
                        buf.getLong();
                        ++i;
                        break;
                    case CONSTANT_Double:
                        buf.getDouble();
                        ++i;
                        break;
                    case CONSTANT_Class:
                        this.cpClasses[i] = buf.getShort();
                        break;
                    case CONSTANT_String:
                    case CONSTANT_MethodType:
                        buf.getChar();
                        break;
                    case CONSTANT_FieldRef:
                    case CONSTANT_MethodRef:
                    case CONSTANT_InterfaceMethodRef:
                    case CONSTANT_NameAndType:
                        buf.getChar();
                        buf.getChar();
                        break;
                    case CONSTANT_MethodHandle:
                        buf.get();
                        buf.getChar();
                        break;
                    case CONSTANT_InvokeDynamic:
                        buf.getChar();
                        buf.getChar();
                        break;
                }
            }

            buf.getChar();
            this.name = this.cpStrings[this.cpClasses[buf.getChar()]];
            this.superClass = this.cpStrings[this.cpClasses[buf.getChar()]];
            this.interfaces = new String[buf.getChar()];

            for (int i = 0; i < this.interfaces.length; i++) {
                this.interfaces[i] = this.cpStrings[this.cpClasses[buf.getChar()]];
            }
        }

        private String decodeString(ByteBuffer buf) {
            int size = buf.getChar();
            int oldLimit = buf.limit();
            buf.limit(buf.position() + size);
            StringBuilder sb = new StringBuilder(size + (size >> 1) + 16);

            while (buf.hasRemaining()) {
                byte b = buf.get();
                if (b > 0) {
                    sb.append((char)b);
                } else {
                    int b2 = buf.get();
                    if ((b & 240) != 224) {
                        sb.append((char)((b & 31) << 6 | b2 & 63));
                    } else {
                        int b3 = buf.get();
                        sb.append((char)((b & 15) << 12 | (b2 & 63) << 6 | b3 & 63));
                    }
                }
            }

            buf.limit(oldLimit);
            return sb.toString();
        }

        public String getName() {
            return this.name;
        }

        public String getSuperClass() {
            return this.superClass;
        }

        public String[] getInterfaces() {
            return this.interfaces;
        }

        public int getPriority() {
            return priority;
        }

        public void setPriority(int priority) {
            this.priority = priority;
        }

        public byte[] getClassData() {
            return this.classData;
        }

        @Override
        public int compareTo(ClassFile o) {
            return o.priority - this.priority;
        }
    }

    private static String convertByteArrayToCppString(byte[] data) {
        StringBuilder result = new StringBuilder();
        result.append("{ ");
        for (byte b : data) {
            result.append(b).append(", ");
        }
        return result.append("}").toString();
    }

    private static byte[] readStreamFully(InputStream stream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        while (true) {
            int read = stream.read(buffer);

            if (read == -1) {
                break;
            }

            outputStream.write(buffer, 0, read);
        }
        return outputStream.toByteArray();
    }

    private static void processInjectorClass(String inputPath, String outputPath) throws IOException {
        byte[] classData = readStreamFully(Files.newInputStream(Paths.get(inputPath)));
        String cppDataString = convertByteArrayToCppString(classData);

        StringBuilder resultFile = new StringBuilder();
        resultFile.append("#ifndef CLASSES_INJECTOR_H_\n#define CLASSES_INJECTOR_H_\n\n#include \"../jvm/jni.h\"\n\n")
                .append("inline const char* injector_name = \"ru.magnus0x11.injection.Injection\";\n")
                .append("const jbyte injector_class_data[] = ")
                .append(cppDataString)
                .append(";\n\n#endif  //CLASSES_INJECTOR_H_");

        try (OutputStream outputStream = Files.newOutputStream(Paths.get(outputPath))) {
            outputStream.write(resultFile.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    static String readAll(InputStream is) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int r;
        while ((r = is.read(buf)) != -1) {
            out.write(buf, 0, r);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    static String escapeForCStringSingleLine(String prefix, String s) {
        StringBuilder sb = new StringBuilder();
        sb.append("\nconst char* ").append(prefix).append(" = ");
        if (s != null) {
            sb.append("\"");
        }

        if (s != null) {
            sb.append(
                    s.replace("\\", "\\\\")
                            .replace("\"", "\\\"")
                            .replace("\r", "")
                            .replace("\n", "")
            );
        } else {
            sb.append("nullptr");
        }

        if (s != null) {
            sb.append("\"");
        }
        sb.append(";\n");
        return sb.toString();
    }

    static String modConfig = null;
    static String mixinConfig = null;
    static String mixinRefMap = null;
    static String accessWidener = null;

    private static void processInputJar(String inputPath, String outputPath) throws IOException {
        Map<String, ClassFile> classes = new HashMap<>();
        Map<String, ClassFile> mixins = new HashMap<>();

        try (JarFile jarFile = new JarFile(inputPath)) {
            for (ZipEntry entry : jarFile.stream().collect(Collectors.toList())) {
                if (entry.getName().endsWith(".mod.json")) {
                    try (InputStream is = jarFile.getInputStream(entry)) {
                        String json = readAll(is);
                        modConfig = escapeForCStringSingleLine("mod_config", json);
                    }
                }
                if (entry.getName().contains("mixin") && entry.getName().endsWith(".json")) {
                    try (InputStream is = jarFile.getInputStream(entry)) {
                        String json = readAll(is);
                        mixinConfig = escapeForCStringSingleLine("mixin_config", json);
                    }
                }
                if (entry.getName().contains("refmap")) {
                    try (InputStream is = jarFile.getInputStream(entry)) {
                        String json = readAll(is);
                        mixinRefMap = escapeForCStringSingleLine("mixin_refmap", json);
                    }
                }
                if (entry.getName().contains("accesswidener")) {
                    try (InputStream is = jarFile.getInputStream(entry)) {
                        String json = readAll(is);
                        accessWidener = escapeForCStringSingleLine("access_widener", json);
                    }
                }
                if (!entry.getName().endsWith(".class")) {
                    continue;
                }
                try (InputStream stream = jarFile.getInputStream(entry)) {
                    ClassFile classFile = new ClassFile(readStreamFully(stream));
                    if (entry.getName().contains("/mixin/")) {
                        mixins.put(classFile.getName(), classFile);
                    } else {
                        classes.put(classFile.getName(), classFile);
                    }
                }
            }
        }

        System.out.println("Processing " + classes.size() + " classes");

        sortMap(classes);
        sortMap(mixins);

        System.out.println("Load priority calculated, sorting...");

        List<ClassFile> resultClasses = new ArrayList<>(classes.values());
        Collections.sort(resultClasses);

        List<ClassFile> resultMixins = new ArrayList<>(mixins.values());
        Collections.sort(resultMixins);

        List<String> classDataStrings = new ArrayList<>();
        for (ClassFile file : resultClasses) {
            classDataStrings.add(convertByteArrayToCppString(file.getClassData()));
        }

        List<String> resultMixinDataStrings = new ArrayList<>();
        for (ClassFile file : resultMixins) {
            resultMixinDataStrings.add(convertByteArrayToCppString(file.getClassData()));
        }

        if (modConfig == null) {
            System.out.println("mod config is null error");
            System.exit(0);
        }
        if (mixinConfig == null) {
            System.out.println("mixin config is null error");
            System.exit(0);
        }

        StringBuilder resultFile = new StringBuilder();
        resultFile.append("#ifndef CLASSES_JAR_H_\n#define CLASSES_JAR_H_\n\n#include \"../jvm/jni.h\"\n\n");

        resultFile.append(modConfig);
        resultFile.append(mixinConfig);
        if (mixinRefMap != null) {
            resultFile.append(mixinRefMap);
        } else {
            resultFile.append(escapeForCStringSingleLine("mixin_refmap", null));
        }
        if (accessWidener != null) {
            resultFile.append(accessWidener);
        } else {
            resultFile.append(escapeForCStringSingleLine("access_widener", null));
        }
        resultFile.append("\n");

        writeData(resultFile, "other", resultClasses, classDataStrings);
        resultFile.append("\n\n");
        writeData(resultFile, "mixin", resultMixins, resultMixinDataStrings);
        resultFile.append("#endif  //CLASSES_JAR_H_");

        try (OutputStream outputStream = Files.newOutputStream(Paths.get(outputPath))) {
            outputStream.write(resultFile.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    public static void writeData(StringBuilder builder, String prefix, List<ClassFile> resultClasses, List<String> classDataStrings) {
        for (int i = 0; i < classDataStrings.size(); i++) {
            builder.append("const jbyte ").append(prefix).append("_class_data_").append(i).append("[] = ").append(classDataStrings.get(i)).append(";\n");
        }
        builder.append("\nconst jbyte* ").append(prefix).append("_classes_data[] = { ");
        for (int i = 0; i < classDataStrings.size(); i++) {
            builder.append(prefix).append("_class_data_").append(i).append(", ");
        }
        builder.append("};\n\nconst jint ").append(prefix).append("_classes_sizes[] = {");
        for (ClassFile file : resultClasses) {
            builder.append(file.getClassData().length).append(", ");
        }
        builder.append("};\n\n");
    }

    public static Map<String, ClassFile> sortMap(Map<String, ClassFile> map) {
        map.values().forEach((class1) -> {
            map.values().forEach((class2) -> {
                if (map.containsKey(class2.getSuperClass())) {
                    map.get(class2.getSuperClass()).setPriority(
                            Math.max(map.get(class2.getSuperClass()).getPriority(),
                                    class2.getPriority() + 1)
                    );
                }

                for (String superInterface : class2.getInterfaces()) {
                    if (map.containsKey(superInterface)) {
                        map.get(superInterface).setPriority(
                                Math.max(map.get(superInterface).getPriority(),
                                        class2.getPriority() + 1)
                        );
                    }
                }
            });
        });

        return map;
    }

    public static void main(String[] args) throws Throwable {
//        if (true) {
//            processInputJar("test2.jar", "jar.h");
//            processInjectorClass("Injection.class", "injector");
//
//            return;
//        }

        if (args.length != 3) {
            throw new RuntimeException("Wrong number of arguments, usage: <injector/input-jar> <input jar or class file> <output path>");
        }

        String type = args[0];
        String input = args[1];
        String output = args[2];

        switch (type) {
            case "injector":
                processInjectorClass(input, output);
                break;
            case "input-jar":
                processInputJar(input, output);
                break;
        }

        System.out.println("Ready for " + type);
    }
}