package com.jawspeak.unifier.spike;

import static com.google.classpath.RegExpResourceFilter.ANY;
import static com.google.classpath.RegExpResourceFilter.ENDS_WITH_CLASS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Stack;

import org.junit.BeforeClass;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import com.google.classpath.ClassPath;
import com.google.classpath.ClassPathFactory;
import com.google.classpath.RegExpResourceFilter;

public class ReadJarWriteJarTest {
  private ClassPath classPath;
  private static final String GENERATED_BYTECODE = "target/test-generated-bytecode";
  
  @BeforeClass
  public static void setUpOnce() {
    File toDelete = new File(GENERATED_BYTECODE);
//    System.out.println(toDelete + " exists? " + toDelete.exists());
//    stackDelete(toDelete);
    recursiveDelete(new File(GENERATED_BYTECODE));
//    System.out.println(toDelete + " exists? " + toDelete.exists());
  }

  // this is a spike, so hey, I can implement this just for fun
  private static void stackDelete(File file) {  
    Stack<File> toDelete = new Stack<File>();
    toDelete.add(file);
    while (!toDelete.isEmpty()) {
      File f = toDelete.peek();
      if (f.isDirectory() && f.listFiles().length == 0) { // empty directory
        toDelete.pop().delete();
      } else if (f.isDirectory()) { // full directory
        for (File listing : f.listFiles()) {
          toDelete.push(listing);
        }
      } else { // file
        toDelete.pop().delete();
      }
    }
  }

  
  private static void recursiveDelete(File file) {
    if (file.isDirectory()) {
      for (File listing : file.listFiles()) {
        recursiveDelete(listing);
      }
      file.delete();
    } else {
      file.delete();
    }
  }

  @Test
  public void readsAndThenWritesJar() throws Exception {
    classPath = new ClassPathFactory().createFromPath("src/test/resources/single-class-in-jar.jar");
    String[] resources = classPath.findResources("", new RegExpResourceFilter(ANY, ENDS_WITH_CLASS));
    System.out.println("resources=" + Arrays.deepToString(resources));
    
    assertFalse("no dots in path", classPath.isPackage("."));
    assertFalse(classPath.isPackage("com.jawspeak.unifier.dummy"));
    assertTrue(classPath.isPackage("/"));
    assertTrue(classPath.isPackage("/com"));
    assertTrue(classPath.isPackage("com"));
    assertTrue(classPath.isPackage("com/jawspeak/unifier/dummy"));
    assertTrue(classPath.isPackage("com/jawspeak/unifier/dummy/"));
    assertTrue(classPath.isResource("com/jawspeak/unifier/dummy/DoNothingClass1.class"));
    
    String generatedBytecodeDir = GENERATED_BYTECODE + "/read-then-write-jar/";
    writeOutDirectFiles(generatedBytecodeDir, resources);
    
    classPath = new ClassPathFactory().createFromPath(generatedBytecodeDir);
    assertTrue(classPath.isPackage("/"));
    assertTrue(classPath.isPackage("/com"));
    assertTrue(classPath.isPackage("com"));
    assertTrue(classPath.isPackage("com/jawspeak/unifier/dummy"));
    assertTrue(classPath.isPackage("com/jawspeak/unifier/dummy/"));
    assertTrue(classPath.isResource("com/jawspeak/unifier/dummy/DoNothingClass1.class"));
  }

  @Test
  public void readsPassesThroughAsmThenWritesJar() throws Exception {
    classPath = new ClassPathFactory().createFromPath("src/test/resources/single-class-in-jar.jar");
    String[] resources = classPath.findResources("", new RegExpResourceFilter(ANY, ENDS_WITH_CLASS));
    System.out.println("resources=" + Arrays.deepToString(resources));
    
    assertFalse("no dots in path", classPath.isPackage("."));
    assertFalse(classPath.isPackage("com.jawspeak.unifier.dummy"));
    assertTrue(classPath.isPackage("/"));
    assertTrue(classPath.isPackage("/com"));
    assertTrue(classPath.isPackage("com"));
    assertTrue(classPath.isPackage("com/jawspeak/unifier/dummy"));
    assertTrue(classPath.isPackage("com/jawspeak/unifier/dummy/"));
    assertTrue(classPath.isResource("com/jawspeak/unifier/dummy/DoNothingClass1.class"));
    final byte[] originalClassBytes = readInputStream(classPath.getResourceAsStream("com/jawspeak/unifier/dummy/DoNothingClass1.class")).toByteArray();
    
    String generatedBytecodeDir = GENERATED_BYTECODE + "/read-then-asm-passthrough-write-jar/";
    writeOutAsmFiles(generatedBytecodeDir, resources);
    
    classPath = new ClassPathFactory().createFromPath(generatedBytecodeDir);
    assertTrue(classPath.isPackage("/"));
    assertTrue(classPath.isPackage("/com"));
    assertTrue(classPath.isPackage("com"));
    assertTrue(classPath.isPackage("com/jawspeak/unifier/dummy"));
    assertTrue(classPath.isPackage("com/jawspeak/unifier/dummy/"));
    assertTrue(classPath.isResource("com/jawspeak/unifier/dummy/DoNothingClass1.class"));
    final byte[] newBytes = readInputStream(classPath.getResourceAsStream("com/jawspeak/unifier/dummy/DoNothingClass1.class")).toByteArray();
    assertTrue(newBytes.length > 0);
    
    class MyClassLoader extends ClassLoader {
      Class<?> clazz;
    }
    MyClassLoader originalClassLoader = new MyClassLoader() {{
      clazz = defineClass("com.jawspeak.unifier.dummy.DoNothingClass1", originalClassBytes, 0, originalClassBytes.length);
    }};
    MyClassLoader newClassLoader = new MyClassLoader() {{
      clazz = defineClass("com.jawspeak.unifier.dummy.DoNothingClass1", newBytes, 0, newBytes.length);
    }};
    
    // load from both classloaders, and the methods should be the same. Could test more, but this
    // proves the spike of reading from asm and writing back out.
    Class<?> originalClass = originalClassLoader.clazz;
    Class<?> newClass = newClassLoader.clazz;
    Method[] originalMethods = originalClass.getMethods();
    Method[] newMethods = newClass.getMethods();
    assertEquals(originalMethods.length, newMethods.length);
    for (int i = 0; i < originalMethods.length; i++) { 
      assertEquals(originalMethods[i].toString(), newMethods[i].toString());
    }
  }

  
  
  private void writeOutDirectFiles(String outputDir, String[] resources) throws IOException {
    File outputBase = new File(outputDir);
    outputBase.mkdir();
    for (String resource : resources) {
      String[] pathAndFile = splitResourceToPathAndFile(resource);
      File output = new File(outputBase, pathAndFile[0]);
      output.mkdirs();
      InputStream is = classPath.getResourceAsStream(resource);
      ByteArrayOutputStream baos = readInputStream(is);
      FileOutputStream os = new FileOutputStream(new File(output, pathAndFile[1]));
      os.write(baos.toByteArray());
      os.close();
    }
  }

  private void writeOutAsmFiles(String outputBaseDir, String[] resources) throws IOException {
    File outputBase = new File(outputBaseDir);
    outputBase.mkdir();
    for (String resource : resources) {
      String[] pathAndFile = splitResourceToPathAndFile(resource);
      File packageDir = new File(outputBase, pathAndFile[0]);
      packageDir.mkdirs();
      InputStream is = classPath.getResourceAsStream(resource);
      
      // Here's the key: read from the old bytes and write to the new ones. 
      // Ends up just copying the byte array, but next we'll look at inserting something 
      // interesting in between them.
      ClassReader reader = new ClassReader(is);
      ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
      reader.accept(writer, 0);
      FileOutputStream os = new FileOutputStream(new File(packageDir, pathAndFile[1]));
      os.write(writer.toByteArray());
      os.close();
    }
  }
  
  private ByteArrayOutputStream readInputStream(InputStream is) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    int toRead;
    int offset = 0;
    while((toRead = is.available()) > 0) {
      byte[] buf = new byte[toRead];
      is.read(buf, offset, toRead);
      baos.write(buf);
      offset += toRead;
    }
    is.close();
    return baos;
  }

  private String[] splitResourceToPathAndFile(String resource) {
    int i = resource.length() - 1;
    while (i >= 0) {
      if (resource.charAt(i) == '/') {
        return new String[] {resource.substring(0, i), resource.substring(i + 1)};
      }
      i--;
    }
    return new String[] {"", resource};
  }
}