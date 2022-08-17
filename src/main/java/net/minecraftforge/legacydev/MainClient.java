/*
 * LegacyDev
 * Copyright (c) 2016-2020.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package net.minecraftforge.legacydev;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

public class MainClient extends Main {
    public static void main(String[] args) throws Exception {
        new MainClient().start(args);
    }

    @Override
    protected void handleNatives(String path) {
        String paths = System.getProperty("java.library.path");

        if (paths == null || paths.isEmpty())
            paths = path;
        else
            paths += File.pathSeparator + path;

        System.setProperty("java.library.path", paths);

        // hack the classloader now.
        try {
            final Method initializePathMethod = ClassLoader.class.getDeclaredMethod("initializePath", String.class);
            initializePathMethod.setAccessible(true);
            final Object usrPathsValue = initializePathMethod.invoke(null, "java.library.path");
            final Field usrPathsField = ClassLoader.class.getDeclaredField("usr_paths");
            usrPathsField.setAccessible(true);
            usrPathsField.set(null, usrPathsValue);
        }
        catch(Throwable t) {};
    }

    @Override
    protected void extractResources() {
        String assetsDirPath = getenv("assetDirectory");
        
        if (assetsDirPath == null) {
            LOGGER.warning("Could not locate assets index directory, skipping resources extraction");
            return;
        }

        final Path resourcesDir = getGameDir().toPath().resolve("resources");
        Path assetsDir = Paths.get(assetsDirPath);

        try {
            LOGGER.info("Extracting resources");
            long startMillis = System.currentTimeMillis();
            
            if (Files.notExists(resourcesDir)) Files.createDirectory(resourcesDir);
            
            Gson gson = new Gson();
            JsonObject node = gson.fromJson(Files.newBufferedReader(assetsDir.resolve("indexes/pre-1.6.json"), StandardCharsets.UTF_8), JsonObject.class);
            final Multimap<String, String> hashToName = HashMultimap.create();
            for (Map.Entry<String, JsonElement> entry : node.getAsJsonObject("objects").entrySet()) {
                hashToName.put(entry.getValue().getAsJsonObject().get("hash").getAsString(), entry.getKey());
            }
            
            final AtomicInteger count = new AtomicInteger(0);
            Files.walkFileTree(assetsDir.resolve("objects"), new FileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Collection<String> names = hashToName.get(file.getFileName().toString());
                    for (String name : names) {
                        Path dest = resourcesDir.resolve(name);
                        File destFile = dest.toFile();
                        if (!destFile.exists()) {
                            try {
                                destFile.getParentFile().mkdirs();
                                Files.copy(file, dest);
                                count.incrementAndGet();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }
            });
            
            if (count.get() > 0) {
                long endMillis = System.currentTimeMillis();
                long delta = endMillis - startMillis;
                LOGGER.log(Level.INFO, "Extracted {0} resources in {1} ms", new Object[] { count.get(), delta });
            }
        } catch (IOException e) {
            LOGGER.severe("Failed to extract resources");
            e.printStackTrace();
        }
    }
    
    private File getGameDir() {
        String str = System.getProperty("minecraft.applet.TargetDirectory");
        if (str != null) {
            return new File(str.replace('/', File.separatorChar));
        }
        return new File(".");
    }
}
