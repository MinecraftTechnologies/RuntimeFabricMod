/*
 * Copyright 2016 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.magnus0x11.injection;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigSource;
import org.spongepowered.asm.mixin.transformer.Config;
import org.spongepowered.asm.mixin.transformer.ext.Extensions;
import org.spongepowered.asm.service.IMixinService;
import org.spongepowered.asm.service.MixinService;

import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.discovery.ModCandidateImpl;
import net.fabricmc.loader.impl.game.GameProvider;
import net.fabricmc.loader.impl.metadata.BuiltinModMetadata;
import net.fabricmc.loader.impl.metadata.LoaderModMetadata;

/**
 *  Coded by Magnus0x11 (TwentyBytes) 17.01.2026.
 *
 *  Версии Fabric:
 *  	- fabric-loader = 0.18.4
 *  	- asm_version = 9.9
 * 		- mixin_version = 0.17.0+mixin.0.8.7
 * 		- mixin_extras_version = 0.5.0
 *
 * 	Для редактированния данного класса необходимо поместить его в main
 * 	пакет Fabric Loader (Src).
 *
 *  Использованное ПО:
 *  	- radioegor146 (<a href="https://github.com/radioegor146/jar-to-dll">ссылка на проект</a>) - глубокое изменение.
 *  	- rdbo (<a href="https://github.com/rdbo/jnihook">ссылка на проект</a>)
 *
 *  Для анализа:
 *    	- Recaf (анализ классов)
 *      - ByteCodeViewer (анализ байткода сгенерированных/пропатченных классов)
 *      - IntellijIdea (IDE + debug)
 *
 *  Помогли:
 *  	- TheXSVV (YouGame, Частичная помощь с JNI)
 *  	- pia1337 (Частичная помощь с JNI)
 *
 * 	Часть кода сгенерирована с помощью нейросети. Большая часть этого
 * 	сгенерированного кода была сильно отредактирована.
 *
 * 	Использованные нейронки:
 * 		- Gemini Pro
 * 		- ChatGPT 5.0
 */
public class Injection extends URLStreamHandler {

	public static Injection INSTANCE;
	public static boolean printDebugClasses = false;

	public static String entryPoint = null;
	public static Class<?> entryPointClass = null;
	public static ModCandidateImpl candidate = null;
	public static boolean client = false;
	public static Constructor<?> urlMemoryConnectionConstructor = null;
	public static Map<String, byte[]> mixinsMap = new HashMap<>();
	public static String mixinRefMap = null;

	public Injection() {
		INSTANCE = this;
	}

	/**
	 * Проверяет, что данный класс загружен класс-лоадером Knot и
	 * находит KnotClassDelegate. После этого продолжает инициализацию в другом методе.
	 *
	 * @param mixinsConfig         - конфиг миксинов, из мода (resources).
	 * @param fabricModInfo        - modinfo из мода (resources).
	 * @param mixinRefMap 		   - referrence map для миксинов из мода (resources).
	 * @param accessWidener    	   - config access widener из мода (resources).
	 * @param mixins               - классы миксин для применения.
	 * @param otherClasses         - остальные классы, помимо миксин (вся остальная логика)
	 */
	public static void start(
			String mixinsConfig, String fabricModInfo,
			String mixinRefMap, String accessWidener,
			byte[][] mixins, byte[][] otherClasses
	) throws NoSuchFieldException, IllegalAccessException {
		try {
			new Injection();

			ClassLoader loader = Injection.class.getClassLoader();

			System.out.println("[Injector] ClassLoader -> " + loader.getClass().getName());

			if (!loader.getClass().getName().endsWith("KnotClassLoader")) {
				System.out.println("[Injector] Error, loaded by other classloader, not KnotClassLoader. Check native load method.");
				System.exit(0);
			}

			Field delegate = loader.getClass().getDeclaredField("delegate");
			delegate.setAccessible(true);
			Object knotClassDelegate = delegate.get(loader);

			loadClasses(new byte[][]{generateMemoryURLConnection()}, clazz -> {
				try {
					urlMemoryConnectionConstructor = clazz.getDeclaredConstructor(URL.class, byte[].class);
				} catch (NoSuchMethodException e) {
					throw new RuntimeException(e);
				}
				System.out.println("[Injector] Generated and loaded class: " + clazz.getName());
			});

			loadModInMemory(fabricModInfo, otherClasses);
			initMixins(knotClassDelegate, mixinRefMap, mixinsConfig, mixins);

			System.out.println("[Injector] Finished first java part.");
		} catch (Throwable throwable) {
			throwable.printStackTrace();
		}
	}


	/**
	 * Загружает классы мода в память и сохраняет в переменные entrypoint, ModCandidate
	 * для инициализации мода по сигналу нативки.
	 *
	 * @param fabricModInfo fabric.mod.json
	 * @param otherClasses  байт-код классов мода
	 */
	public static void loadModInMemory(String fabricModInfo, byte[][] otherClasses) throws Exception {
		ModMetadata metadata = getMetadata(fabricModInfo);

		GameProvider.BuiltinMod builtinMod = new GameProvider.BuiltinMod(Collections.singletonList(Paths.get(System.getProperty("java.home"))), metadata);
		Constructor<?> constructor = ModCandidateImpl.class.getDeclaredConstructors()[0];
		constructor.setAccessible(true);

		Class<?> wrapperClazz = Class.forName("net.fabricmc.loader.impl.discovery.BuiltinMetadataWrapper");
		Constructor<?> wrapperClazzDeclaredConstructor = wrapperClazz.getDeclaredConstructor(ModMetadata.class);
		wrapperClazzDeclaredConstructor.setAccessible(true);
		LoaderModMetadata wrappedMetadata = (LoaderModMetadata) wrapperClazzDeclaredConstructor.newInstance(metadata);

		candidate = (ModCandidateImpl) constructor.newInstance(builtinMod.paths, (String) null, -1L, wrappedMetadata, false, Collections.emptyList());

		loadClasses(otherClasses, clazz -> {
			System.out.println("[Injector] Loaded mod class: " + clazz);
			if (clazz != null && clazz.getName().equalsIgnoreCase(entryPoint)) {
				entryPointClass = clazz;
			}
		});
	}

	/**
	 * Регистрация в Fabric и вызов entrypoint
	 * Метод вызывается из натива.
	 */
	public static void initMod() throws InvocationTargetException, InstantiationException, IllegalAccessException, NoSuchMethodException {
		System.out.println("[Injector] Init and register fabric mod started.");

		Constructor<?> constructor = entryPointClass.getConstructors()[0];
		constructor.setAccessible(true);

		Object modInst = constructor.newInstance();

		// add mod in fabric
		FabricLoaderImpl loaderInst = FabricLoaderImpl.INSTANCE;
		Class<? extends FabricLoaderImpl> loaderClazz = loaderInst.getClass();

		Method addMod = loaderClazz.getDeclaredMethod("addMod", ModCandidateImpl.class);
		addMod.setAccessible(true);
		addMod.invoke(loaderInst, candidate);

		// call entrypoint
		Method onInit;

		if (client) {
			onInit = modInst.getClass().getMethod("onInitializeClient");
		} else {
			onInit = modInst.getClass().getMethod("onInitialize");
		}

		onInit.setAccessible(true);
		onInit.invoke(modInst);

		System.out.println("[Injector] Init and register fabric mod finished. Latest java part ended.");
	}

	// UTILITY ||
	// 		   \/

	/**
	 * @param modInfo fabric.mod.json
	 * @return - ModMetadata + EntryPoint (MainClass)
	 */
	public static ModMetadata getMetadata(String modInfo) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, InstantiationException {
		Gson gson = new Gson();

		JsonObject json = gson.fromJson(
				modInfo,
				JsonObject.class
		);

		if (json.has("accessWidener")) {
			System.out.println("[Injector] Access widener does`nt supported");
			System.exit(0);
			return null;
		}

		JsonObject entrypoints = json.get("entrypoints")
				.getAsJsonObject();

		entryPoint = parseEntryPoint(entrypoints);

		ModMetadata metadata = new BuiltinModMetadata.Builder(json.get("id").getAsString(), json.get("version").getAsString())
				.setName(json.get("name").getAsString())
				.setDescription("Injected mod")
				.build();

		return metadata;
	}

	public static String parseEntryPoint(JsonObject entrypoints) {
		if (!entrypoints.has("client") && !entrypoints.has("main")) {
			System.out.println("[Injector] No entrypoint found");
			System.exit(0);
			return null;
		}

		if (entrypoints.has("client")) {
			client = true;

			return entrypoints.get("client")
					.getAsJsonArray()
					.get(0)
					.getAsString();
		} else {
			return entrypoints.get("main")
					.getAsJsonArray()
					.get(0)
					.getAsString();
		}
	}

	public static void loadClasses(byte[][] classes, Consumer<Class<?>> loadedCallback) throws Exception {
		ClassLoader loader = Injection.class.getClassLoader();

		Method method = Injection.class.getClassLoader()
				.getClass()
				.getMethod("defineClassFwd", String.class, byte[].class, int.class, int.class, CodeSource.class);

		method.setAccessible(true);

		for (byte[] classData : classes) {
			if (classData == null) {
				throw new Exception("classData is null");
			}

			try {
				Class<?> clazz = null;
				try {
					//loader, null, classData, 0, classData.length, loader.getClass().getProtectionDomain()
					clazz = (Class<?>) method.invoke(loader, null, classData, 0, classData.length, null);
				} catch (Throwable throwable) {
					if (!(throwable instanceof LinkageError)) {
						throw throwable;
					}

					if (throwable.getMessage().contains("duplicate class definition for name: ")) {
						String className = throwable.getMessage().split("\"")[1];
						clazz = loader.loadClass(className.replace('/', '.'));
					}
				}

				if (loadedCallback != null) {
					loadedCallback.accept(clazz);
				}
			} catch (Exception e) {
				//noinspection CallToPrintStackTrace
				e.printStackTrace();
				throw new Exception("Exception on defineClass", e);
			}
		}
	}

	// MIXINS

	/**
	 * @param knotClassDelegate instance KnotClassDelegate.class
	 * @param mixinsConfig      - конфигурация миксин с мода
	 * @param mixins            - классы миксин
	 */
	public static void initMixins(Object knotClassDelegate, String mixinRefmap, String mixinsConfig, byte[][] mixins) throws Exception {
		Set<Class<?>> mixinApplyClasses = new HashSet<>();

		Injection.mixinRefMap = mixinRefmap;

		Gson gson = new Gson();
		JsonObject jsonObject = gson.fromJson(mixinsConfig, JsonObject.class);
		jsonObject.addProperty("required", false);
		mixinsConfig = gson.toJson(jsonObject);

		mixinsMap = new HashMap<>();

		for (byte[] mixin : mixins) {
			ClassNode read = readClass(mixin);
			mixinsMap.put(read.name, mixin);

			if (read.invisibleAnnotations == null) {
				System.out.println("[Injector] No @Mixin annotation found for class " + read.name);
				System.exit(0);
				continue;
			}

			blockCheckAnnotations:
			{
				for (AnnotationNode annotation : read.invisibleAnnotations) {
					if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) {
						continue;
					}

					if (annotation.values == null) {
						System.out.println("[Injector] @Mixin annotation found for class " + read.name + ", but body is empty (values = null or empty arr)");
						System.exit(0);
						return;
					}

					@SuppressWarnings("unchecked")
					List<Object> list = (List<Object>) annotation.values.get(1);
					for (Object object : list) {
						Type type = (Type) object;
						String className = type.getClassName();
						System.out.println("[Injector] Funded mixin for " + className + ", with name " + read.name);
						mixinApplyClasses.add(Class.forName(className, false, Injection.class.getClassLoader()));
					}

					break blockCheckAnnotations;
				}

				System.out.println("[Injector] No @Mixin annotation found for class " + read.name);
				System.exit(0);
			}
		}

		Map<Class<?>, byte[]> bytesBefore = new HashMap<>();

		mixinApplyClasses.forEach(klass -> {
			try {
				bytesBefore.put(klass, getClassBytes(knotClassDelegate, klass.getName()));
			} catch (Throwable e) {
				throw new RuntimeException(e);
			}
		});

		// Loading mixins in cache

		ClassLoader loader = Injection.class.getClassLoader();
		Method addUrlFwd = loader.getClass().getMethod("addUrlFwd", URL.class);
		addUrlFwd.setAccessible(true);

		URL memoryUrl = new URL("memory", "", -1, "/", INSTANCE);
		addUrlFwd.invoke(loader, memoryUrl);

//		mixinsMap.keySet().forEach(klass -> {
//			try {
//				URL memoryUrl = new URL("memory", "", -1, "/", INSTANCE);
//				addUrlFwd.invoke(loader, memoryUrl);
//
//				System.out.println("[Injector] Registered mixin " + klass + ".class with MemoryURL");
//			} catch (Throwable e) {
//				throw new RuntimeException(e);
//			}
//		});

		// Loading mixins end

		Class<?> mixinConfigClazz = Class.forName("org.spongepowered.asm.mixin.transformer.MixinConfig");
		Object mixinConfig = null;

		try (InputStream inputStream = new ByteArrayInputStream(mixinsConfig.getBytes(StandardCharsets.UTF_8)); InputStreamReader inputStreamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);) {
			// ебанутые из SpongePowered накодили свою версию Gson
			// которая как-то по другому читает все. Поэтому юзаем ее.
			// без этого нихуя не считается и вам сгенерит пустой конфиг
			mixinConfig = new org.spongepowered.include.com.google.gson.Gson().fromJson(inputStreamReader, mixinConfigClazz);
		}
		Method onLoad = mixinConfigClazz.getDeclaredMethod("onLoad", IMixinService.class, String.class, MixinEnvironment.class, IMixinConfigSource.class);
		onLoad.setAccessible(true);
		boolean invoke = (boolean) onLoad.invoke(mixinConfig, MixinService.getService(), "dynamic_mixins" + System.currentTimeMillis() + ".json", MixinEnvironment.getDefaultEnvironment(), (IMixinConfigSource) null);
		if (!invoke) {
			System.out.println("[Injector] Error when init mixins config");
			System.exit(0);
			return;
		}

		Method selectMethod = mixinConfig.getClass().getDeclaredMethod("select", MixinEnvironment.class);
		selectMethod.setAccessible(true);
		selectMethod.invoke(mixinConfig, MixinEnvironment.getCurrentEnvironment());

		Method onSelectMethod = mixinConfig.getClass().getDeclaredMethod("onSelect");
		onSelectMethod.setAccessible(true);
		onSelectMethod.invoke(mixinConfig);

		Method getHandleMethod = mixinConfigClazz.getDeclaredMethod("getHandle");
		getHandleMethod.setAccessible(true);
		Config config = (Config) getHandleMethod.invoke(mixinConfig);

		Method registerConfiguration = Mixins.class.getDeclaredMethod("registerConfiguration", Config.class);
		registerConfiguration.setAccessible(true);
		registerConfiguration.invoke(null, config);

		// Register in MixinTransformer

		Field transformerField = knotClassDelegate.getClass().getDeclaredField("mixinTransformer");
		transformerField.setAccessible(true);
		Object transformer = transformerField.get(knotClassDelegate);

		Field processorField = transformer.getClass().getDeclaredField("processor");
		processorField.setAccessible(true);
		Object processor = processorField.get(transformer);

		Field configsField = processor.getClass().getDeclaredField("pendingConfigs");
		configsField.setAccessible(true);
		@SuppressWarnings("rawtypes")
		List configs = (List) configsField.get(processor);
		//noinspection unchecked
		configs.add(mixinConfig);
		configsField.set(processor, configs);

		Field extensionsField = processor.getClass().getDeclaredField("extensions");
		extensionsField.setAccessible(true);
		Object extensions = extensionsField.get(processor);

		Method prepareConfigsMethod = processor.getClass().getDeclaredMethod("prepareConfigs", MixinEnvironment.class, Extensions.class);
		prepareConfigsMethod.setAccessible(true);
		prepareConfigsMethod.invoke(processor, MixinEnvironment.getCurrentEnvironment(), extensions);

		// Mixins.addConfig end

		Map<Class<?>, byte[]> bytesAfter = new HashMap<>();

		mixinApplyClasses.forEach(klass -> {
			try {
				bytesAfter.put(klass, getClassBytes(knotClassDelegate, klass.getName()));
			} catch (Throwable e) {
				throw new RuntimeException(e);
			}
		});

		Set<Class<?>> keys = new HashSet<>(bytesAfter.keySet());

		for (Class<?> aClass : keys) {
			byte[] before = bytesBefore.get(aClass);

			if (printDebugClasses) {
				Files.write(new File("before_us_mixins.class").toPath(), before);
			}

			byte[] after = bytesAfter.get(aClass);

			System.out.println("[Injector] After mixins equality -> " + Arrays.equals(before, after) + ", for class "
					+ aClass.getName());

			System.out.println("[Injector] Try redefineClassNative");
			redefineClass(aClass, postApply(readClass(before), readClass(after)));
		}
	}

	public static ClassNode readClass(byte[] bytes) {
		ClassReader reader = new ClassReader(bytes);
		ClassNode classNode = new ClassNode();
		reader.accept(classNode, 8);
		return classNode;
	}

	// par1 Reference KnotClassDelegate.class
	public static byte[] getClassBytes(Object knotClassDelegateInst, String className) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
		// Класс KnotClassDelegate.class
		Class<?> kClassDelegate = knotClassDelegateInst.getClass();
		// Получает метод дающий байт-код класса после применения миксин
		Method methodGetBytes = kClassDelegate
				.getDeclaredMethod("getPostMixinClassByteArray", String.class, boolean.class);

		methodGetBytes.setAccessible(true);
		@SuppressWarnings("UnnecessaryLocalVariable")
		byte[] clazzAfterMixins = (byte[]) methodGetBytes.invoke(knotClassDelegateInst, className, false); // true тут юзается только для Log4j класса (судя по MinecraftGameProvider.setupLogHandler)

		return clazzAfterMixins;
	}

	// public class MemoryURLConnection extends URLConnection {
	//    private final byte[] data;
	//
	//    protected MemoryURLConnection(URL url, byte[] data) {
	//        super(url);
	//        this.data = data;
	//    }
	//
	//    @Override
	//    public void connect() {
	//        // Соединение всегда "установлено", так как данные в памяти
	//    }
	//
	//    @Override
	//    public InputStream getInputStream() {
	//        return new ByteArrayInputStream(data);
	//    }
	//}

	/**
	 * ** Код написан с помощью Gemini
	 * Генерирует класс URLMemoryConnection, который позволяет загрузить класс
	 * из byte[]
	 *
	 * @return byte[] сгенерированного класса
	 */
	public static byte[] generateMemoryURLConnection() {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);

		// 1. Определение заголовка класса
		// public class MemoryURLConnection extends java.net.URLConnection
		cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC + Opcodes.ACC_SUPER, "MemoryURLConnection", null, "java/net/URLConnection", null);

		// 2. Поле: private final byte[] data;
		cw.visitField(Opcodes.ACC_PRIVATE + Opcodes.ACC_FINAL, "data", "[B", null, null).visitEnd();

		// 3. Конструктор: public MemoryURLConnection(URL url, byte[] data)
		{
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(Ljava/net/URL;[B)V", null, null);
			mv.visitCode();
			mv.visitVarInsn(Opcodes.ALOAD, 0); // this
			mv.visitVarInsn(Opcodes.ALOAD, 1); // url
			mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/net/URLConnection", "<init>", "(Ljava/net/URL;)V", false);

			mv.visitVarInsn(Opcodes.ALOAD, 0); // this
			mv.visitVarInsn(Opcodes.ALOAD, 2); // data
			mv.visitFieldInsn(Opcodes.PUTFIELD, "MemoryURLConnection", "data", "[B");

			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(0, 0); // Автоматически вычисляется благодаря COMPUTE_FRAMES
			mv.visitEnd();
		}

		// 4. Метод: public void connect() { }
		{
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "connect", "()V", null, null);
			mv.visitCode();
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(0, 0);
			mv.visitEnd();
		}

		// 5. Метод: public InputStream getInputStream()
		{
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "getInputStream", "()Ljava/io/InputStream;", null, null);
			mv.visitCode();
			mv.visitTypeInsn(Opcodes.NEW, "java/io/ByteArrayInputStream");
			mv.visitInsn(Opcodes.DUP);
			mv.visitVarInsn(Opcodes.ALOAD, 0); // this
			mv.visitFieldInsn(Opcodes.GETFIELD, "MemoryURLConnection", "data", "[B");
			mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/io/ByteArrayInputStream", "<init>", "([B)V", false);
			mv.visitInsn(Opcodes.ARETURN);
			mv.visitMaxs(0, 0);
			mv.visitEnd();
		}

		cw.visitEnd();
		return cw.toByteArray();
	}

	@Override
	protected URLConnection openConnection(URL u) {
		String path = u.getPath();

		if (path.startsWith("/")) {
			path = path.substring(1);
		}

		// не волнуемся за название, ибо все рефмапы других модов давно высосаны при запуске майна
		if (path.contains("refmap") && mixinRefMap != null) {
			urlMemoryConnectionConstructor.setAccessible(true);
			Object object;
			try {
				object = urlMemoryConnectionConstructor.newInstance(u, mixinRefMap.getBytes(StandardCharsets.UTF_8));
			} catch (Throwable e) {
				throw new RuntimeException(e);
			}
			return (URLConnection) object;
		}

		byte[] data = mixinsMap.get(path.replace(".class", ""));

		if (data == null) {
			return null;
		}

		try {
			urlMemoryConnectionConstructor.setAccessible(true);
			Object object = urlMemoryConnectionConstructor.newInstance(u, data);
			return (URLConnection) object;
		} catch (Throwable e) {
			//noinspection CallToPrintStackTrace
			e.printStackTrace();
		}

		return null;
	}


	// MIXIN EDITOR

	/**
	 * Данная функция выносит новые методы* в статический класс
	 * для возможности использования RedefineClass.
	 * *Новые методы (которые появляются от наших миксин)
	 * /
	 * У старых миксин названия меняются на те что у них были в before.
	 * StaticHandlers грузится в память, byte[] с изменениями в after возвращается взад для redefine.
	 * \
	 * Код был написан Gemini, после чего был глубоко отредактирован.
	 * Часть кода написана ChatCPT.
	 *
	 * @param before - класс до наших миксин
	 * @param after - класс после наших миксин
	 * @return byte[] - измененный after.
	 * @throws Exception - любая параша
	 */
	public static byte[] postApply(ClassNode before, ClassNode after) throws Exception {
		String generatedClassName = "ru/magnus0x11/generated/StaticHandlers_" + System.currentTimeMillis();

		List<MethodNode> movedMethods = new ArrayList<>();
		Map<Integer, MethodNode> otherMixinsMethods = new HashMap<>();

		List<MethodNode> beforeNormalized = new ArrayList<>();
		for (MethodNode method : before.methods) {
			MethodNode cloneMethod = cloneMethod(method);
			normalize(cloneMethod);
			beforeNormalized.add(cloneMethod);
		}

		// 1. Извлекаем методы нашей миксины и УДАЛЯЕМ их из целевого класса
		Iterator<MethodNode> it = after.methods.iterator();
		int index = 0;
		while (it.hasNext()) {
			MethodNode mn = it.next();

			if (mn.invisibleAnnotations == null && mn.visibleAnnotations == null) {
				if (mn.name.startsWith("mixinextras")) {
					block: {
						for (MethodNode methodNode : beforeNormalized) {
							MethodNode cloneMethod = cloneMethod(mn);
							normalize(cloneMethod);
							if (compareMethods(cloneMethod, methodNode)) {
								otherMixinsMethods.put(index, mn);
								break block;
							}
						}

						movedMethods.add(mn);
						it.remove();
					}
				}

				++index;
				continue;
			}

			for (AnnotationNode annotation : mn.visibleAnnotations) {
				if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;")) {
					continue;
				}

				String object = (String) annotation.values.get(1);
				if (Injection.mixinsMap.containsKey(object.replace(".", "/"))) {
					movedMethods.add(cloneMethod(mn));
					it.remove();
				} else {
					otherMixinsMethods.put(index, mn);
				}
			}

			++index;
		}

		Map<String, String> renamedMethods = new HashMap<>();

		for (Map.Entry<Integer, MethodNode> entry : otherMixinsMethods.entrySet()) {
			MethodNode targetMethod = entry.getValue();
			int targetIndex = entry.getKey();

			MethodNode m1 = cloneMethod(targetMethod);
			normalize(m1);

			for (int i = 0; i < before.methods.size(); i++) {
				MethodNode beforeMethod = before.methods.get(i);
				MethodNode m2 = beforeNormalized.get(i);

				if (compareMethods(m1, m2)) {
					renamedMethods.put(targetMethod.name, beforeMethod.name);
					targetMethod.name = beforeMethod.name;

					break;
				}
			}
		}

		if (movedMethods.isEmpty()) {
			return toByteArray(before, false); // TODO check мб тут нужен after а не before
		}

		// 2. Генерируем внешний статический класс
		byte[] externalClass = generateExternalClass(generatedClassName, after, movedMethods);
		if (printDebugClasses) {
			try {
				String[] split = generatedClassName.split("/");
				Files.write(new File(split[split.length - 1] + ".class").toPath(), externalClass);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		loadClasses(new byte[][] {externalClass}, clazz -> System.out.println("[Injector] Loaded generated mixin handler class " + clazz.getName() + ", for mixin-applied class with name " + after.name));

		// 3. Перенаправляем вызовы в ОСТАВШИХСЯ методах Target-класса
		for (MethodNode mn : after.methods) {
			for (AbstractInsnNode insn : mn.instructions.toArray()) {
				if (insn instanceof MethodInsnNode) {
					MethodInsnNode minsn = (MethodInsnNode) insn;
					if (minsn.owner.equals(after.name) && isMoved(minsn.name, movedMethods)) {
						// Меняем INVOKEVIRTUAL на INVOKESTATIC к нашему внешнему классу
						minsn.setOpcode(Opcodes.INVOKESTATIC);
						minsn.owner = generatedClassName;
						// Сигнатура: добавляем 'this' (Target) первым аргументом
						minsn.desc = "(L" + after.name + ";" + minsn.desc.substring(1);
					}
					if (renamedMethods.containsKey(minsn.name)) {
						minsn.name = renamedMethods.get(minsn.name);
					}
				}
			}
		}

		byte[] bytes = toByteArray(after, true);
		if (printDebugClasses) {
			try {
				Files.write(new File("after_patched.class").toPath(), bytes);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		return bytes;
	}

	public static byte[] toByteArray(ClassNode targetClass, boolean flags) throws Exception {
		ClassWriter cw = new ClassWriter(flags ? ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES : 0);
		targetClass.accept(cw);
		return cw.toByteArray();
	}

	private static byte[] generateExternalClass(String generatedClassName, ClassNode targetClass, List<MethodNode> methods) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
		cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, generatedClassName, null, "java/lang/Object", null);

		// Создаем статическую инициализацию MethodHandles
		for (MethodNode mn : methods) {
			String staticDesc = "(L" + targetClass.name + ";" + mn.desc.substring(1);
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mn.name, staticDesc, null, null);

			// Трансформируем инструкции метода, чтобы заменить прямые обращения на MethodHandles
			transformToHandles(generatedClassName, targetClass.name, mn);

			mn.accept(mv);
		}

		// Генерируем вспомогательные методы для работы с MethodHandles
		generateHandleHelpers(generatedClassName, cw);

		cw.visitEnd();
		return cw.toByteArray();
	}

	private static void transformToHandles(String generatedClassName, String targetName, MethodNode mn) {
		ListIterator<AbstractInsnNode> insns = mn.instructions.iterator();
		while (insns.hasNext()) {
			AbstractInsnNode insn = insns.next();

			if (insn instanceof FieldInsnNode) {
				FieldInsnNode f = (FieldInsnNode) insn;
				if (f.owner.equals(targetName)) {
					boolean isGet = f.getOpcode() == Opcodes.GETFIELD;
					String helperName = isGet ? "getField" : "setField";
					String helperDesc = isGet
							? "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Object;"
							: "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;)V";

					InsnList list = new InsnList();
					if (!isGet) {
						// Если это PUTFIELD, нужно упаковать примитив в объект перед вызовом хелпера
						boxPrimitive(list, Type.getType(f.desc));
					}

					list.add(new LdcInsnNode(f.name));
					list.add(new LdcInsnNode(f.desc));
					list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, generatedClassName, helperName, helperDesc, false));

					if (isGet) {
						// Если это GETFIELD, нужно распаковать объект в нужный тип
						unboxPrimitive(list, Type.getType(f.desc));
					}

					insns.set(list.get(0));
					for (int i = 1; i < list.size(); i++) insns.add(list.get(i));
				}
			}
		}
	}

	private static void generateHandleHelpers(String generatedClassName, ClassWriter cw) {
		// Метод getField(Object instance, String name, String desc)
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "getField",
				"(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Object;", null, null);
		mv.visitCode();
		// Здесь используется Java Reflection для простоты реализации хелпера в рантайме
		// В продакшене тут лучше использовать кэшированные MethodHandles
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;", false);
		mv.visitVarInsn(Opcodes.ALOAD, 1);
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getDeclaredField", "(Ljava/lang/String;)Ljava/lang/reflect/Field;", false);
		mv.visitInsn(Opcodes.DUP);
		mv.visitInsn(Opcodes.ICONST_1);
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/reflect/Field", "setAccessible", "(Z)V", false);
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/reflect/Field", "get", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
		mv.visitInsn(Opcodes.ARETURN);
		mv.visitMaxs(3, 3);
		mv.visitEnd();

		// Метод setField(Object instance, String name, String desc, Object value)
		mv = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "setField",
				"(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;)V", null, null);
		mv.visitCode();
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;", false);
		mv.visitVarInsn(Opcodes.ALOAD, 1);
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getDeclaredField", "(Ljava/lang/String;)Ljava/lang/reflect/Field;", false);
		mv.visitInsn(Opcodes.DUP);
		mv.visitInsn(Opcodes.ICONST_1);
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/reflect/Field", "setAccessible", "(Z)V", false);
		// Обработка final полей через рефлексию (сброс флага модификаторов)
		mv.visitInsn(Opcodes.DUP);
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, generatedClassName, "stripFinal", "(Ljava/lang/reflect/Field;)V", false);
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitVarInsn(Opcodes.ALOAD, 3);
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/reflect/Field", "set", "(Ljava/lang/Object;Ljava/lang/Object;)V", false);
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(4, 4);
		mv.visitEnd();

		// Вспомогательный метод stripFinal(Field field)
		generateStripFinal(cw);
	}

	private static void generateStripFinal(ClassWriter cw) {
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "stripFinal", "(Ljava/lang/reflect/Field;)V", null, null);
		mv.visitCode();
		mv.visitLdcInsn(Type.getType(Field.class));
		mv.visitLdcInsn("modifiers");
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getDeclaredField", "(Ljava/lang/String;)Ljava/lang/reflect/Field;", false);
		mv.visitInsn(Opcodes.DUP);
		mv.visitInsn(Opcodes.ICONST_1);
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/reflect/Field", "setAccessible", "(Z)V", false);
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/reflect/Field", "getModifiers", "()I", false);
		mv.visitLdcInsn(-17); // ~Modifier.FINAL (0x00000010)
		mv.visitInsn(Opcodes.IAND);
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/reflect/Field", "setInt", "(Ljava/lang/Object;I)V", false);
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(3, 1);
		mv.visitEnd();
	}

	// --- Утилиты упаковки/распаковки примитивов ---

	private static void boxPrimitive(InsnList list, Type type) {
		if (type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY) return;
		String wrapper = getWrapper(type);
		list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, wrapper, "valueOf", "(" + type.getDescriptor() + ")L" + wrapper + ";", false));
	}

	private static void unboxPrimitive(InsnList list, Type type) {
		if (type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY) {
			list.add(new TypeInsnNode(Opcodes.CHECKCAST, type.getInternalName()));
			return;
		}
		String wrapper = getWrapper(type);
		list.add(new TypeInsnNode(Opcodes.CHECKCAST, wrapper));
		list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, wrapper, type.getClassName() + "Value", "()" + type.getDescriptor(), false));
	}

	private static String getWrapper(Type type) {
		switch (type.getSort()) {
			case Type.BOOLEAN: return "java/lang/Boolean";
			case Type.INT: return "java/lang/Integer";
			case Type.LONG: return "java/lang/Long";
			case Type.DOUBLE: return "java/lang/Double";
			case Type.FLOAT: return "java/lang/Float";
			default: return "java/lang/Object";
		}
	}

	private static MethodNode cloneMethod(MethodNode node) {
		MethodNode copy = new MethodNode(node.access, node.name, node.desc, node.signature, node.exceptions.toArray(new String[0]));
		node.accept(copy);
		return copy;
	}

	private static boolean isMoved(String name, List<MethodNode> moved) {
		for (MethodNode m : moved) if (m.name.equals(name)) return true;
		return false;
	}

	// Method insn compare

	public static void normalize(MethodNode m) {
		// удаляем debug и frames
		for (Iterator<AbstractInsnNode> it = m.instructions.iterator(); it.hasNext(); ) {
			AbstractInsnNode insn = it.next();
			if (insn instanceof LineNumberNode
					|| insn instanceof FrameNode) {
				it.remove();
			}
		}

		m.localVariables = null;
		m.tryCatchBlocks = null;
	}

	public static boolean compareMethods(MethodNode m1, MethodNode m2) {
		InsnList i1 = m1.instructions;
		InsnList i2 = m2.instructions;

		if (i1.size() != i2.size()) {
			return false;
		}

		for (int i = 0; i < i1.size(); i++) {
			AbstractInsnNode a = i1.get(i);
			AbstractInsnNode b = i2.get(i);

			if (!compareInsn(a, b)) {
				return false;
			}
		}
		return true;
	}

	private static boolean compareInsn(AbstractInsnNode a, AbstractInsnNode b) {
		if (a.getType() != b.getType()) return false;
		if (a.getOpcode() != b.getOpcode()) return false;

		switch (a.getType()) {
			case AbstractInsnNode.VAR_INSN:
				return ((VarInsnNode) a).var == ((VarInsnNode) b).var;

			case AbstractInsnNode.INT_INSN:
				return ((IntInsnNode) a).operand == ((IntInsnNode) b).operand;

			case AbstractInsnNode.TYPE_INSN:
				return ((TypeInsnNode) a).desc.equals(((TypeInsnNode) b).desc);

			case AbstractInsnNode.FIELD_INSN:
				FieldInsnNode f1 = (FieldInsnNode) a;
				FieldInsnNode f2 = (FieldInsnNode) b;
				return f1.owner.equals(f2.owner)
						&& f1.name.equals(f2.name)
						&& f1.desc.equals(f2.desc);

			case AbstractInsnNode.METHOD_INSN:
				MethodInsnNode m1 = (MethodInsnNode) a;
				MethodInsnNode m2 = (MethodInsnNode) b;
				return m1.owner.equals(m2.owner)
						&& m1.name.equals(m2.name)
						&& m1.desc.equals(m2.desc);

			case AbstractInsnNode.LDC_INSN:
				return Objects.equals(
						((LdcInsnNode) a).cst,
						((LdcInsnNode) b).cst
				);

			case AbstractInsnNode.IINC_INSN:
				IincInsnNode i1 = (IincInsnNode) a;
				IincInsnNode i2 = (IincInsnNode) b;
				return i1.var == i2.var && i1.incr == i2.incr;

			case AbstractInsnNode.JUMP_INSN:
				// label-ы игнорируем
				return true;

			case AbstractInsnNode.LOOKUPSWITCH_INSN:
			case AbstractInsnNode.TABLESWITCH_INSN:
				// упрощённо — считаем одинаковыми
				return true;

			default:
				return true;
		}
	}

	// HOOKS

	// Works as magic with JNI method hooking
	public static void redefineClass(Class<?> clazz, byte[] bytes) {

	}

}
