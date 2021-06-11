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
package net.minecraftforge.gradle.tweakers;

import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraftforge.gradle.GradleForgeHacks;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public class CoremodTweaker implements ITweaker {
    protected static final Logger LOGGER = LogManager.getLogger("CoremodTweaker");
    private static final String COREMOD_CLASS = "net.minecraftforge.fml.relauncher.CoreModManager";
    private static final String TWEAKER_SORT_FIELD = "tweakSorting";

    @Override
    @SuppressWarnings("unchecked")
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {
        try {
            Field coreModList = Class.forName("net.minecraftforge.fml.relauncher.CoreModManager", true, classLoader).getDeclaredField("loadPlugins");
            coreModList.setAccessible(true);

            // grab constructor.
            Class<ITweaker> clazz = (Class<ITweaker>) Class.forName("net.minecraftforge.fml.relauncher.CoreModManager$FMLPluginWrapper", true, classLoader);
            Constructor<ITweaker> construct = (Constructor<ITweaker>) clazz.getConstructors()[0];
            construct.setAccessible(true);

            Field[] fields = clazz.getDeclaredFields();
            Field pluginField = fields[1];  // 1
            Field fileField = fields[3];  // 3
            Field listField = fields[2];  // 2

            Field.setAccessible(clazz.getConstructors(), true);
            Field.setAccessible(fields, true);

            List<ITweaker> oldList = (List<ITweaker>) coreModList.get(null);

            for (int i = 0; i < oldList.size(); i++) {
                ITweaker tweaker = oldList.get(i);

                if (clazz.isInstance(tweaker)) {
                    Object coreMod = pluginField.get(tweaker);
                    Object oldFile = fileField.get(tweaker);
                    File newFile = GradleForgeHacks.CORE_MAP.get(coreMod.getClass().getCanonicalName());

                    LOGGER.info("Injecting location in coremod {}", coreMod.getClass().getCanonicalName());

                    if (newFile != null && oldFile == null) {
                        // build new tweaker.
                        oldList.set(i, construct.newInstance(fields[0].get(tweaker), // name
                                coreMod, // coremod
                                newFile, // location
                                fields[4].getInt(tweaker), // sort index?
                                ((List<String>) listField.get(tweaker)).toArray(new String[0])));
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.log(Level.ERROR, "Something went wrong with the coremod adding.");
            t.printStackTrace();
        }

        // inject the additional AT tweaker
        String atTweaker = "net.minecraftforge.gradle.tweakers.AccessTransformerTweaker";
        ((List<String>) Launch.blackboard.get("TweakClasses")).add(atTweaker);

        // make sure its after the deobf tweaker
        try {
            Field f = Class.forName(COREMOD_CLASS, true, classLoader).getDeclaredField(TWEAKER_SORT_FIELD);
            f.setAccessible(true);
            ((Map<String, Integer>) f.get(null)).put(atTweaker, 1001);
        } catch (Throwable t) {
            LOGGER.log(Level.ERROR, "Something went wrong with the adding the AT tweaker adding.");
            t.printStackTrace();
        }
    }

    @Override
    public String getLaunchTarget() {
        return null;
    }

    @Override
    public String[] getLaunchArguments() {
        return new String[0];
    }

    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {}
}
