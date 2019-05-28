/*
 * This file is part of SPSSH.
 *
 * SPSSH is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SPSSH is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SPSSH. If not, see <https://www.gnu.org/licenses/>.
 *
 */

package rbptrees.cli;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.DumperOptions.FlowStyle;
import org.yaml.snakeyaml.Yaml;

import nl.tue.geometrycore.util.Pair;
import rbptrees.algo.Algorithm;
import rbptrees.cli.BatchRunner.Result;

/**
 * Reader of files with directives for running in batch mode. Also writer of
 * output files of experiments.
 */
public class BatchIO {

    private static final DumperOptions OPTS = new DumperOptions();
    static {
        OPTS.setIndent(2);
        OPTS.setDefaultFlowStyle(FlowStyle.BLOCK);
    }
    private static final Yaml YAML = new Yaml(OPTS);

    private Map<String, Object> config;

    @SuppressWarnings("unchecked")
    public BatchIO(File file) throws FileNotFoundException {
        this.config = (Map<String, Object>) YAML.load(new FileReader(file));
    }

    @SuppressWarnings("unchecked")
    public List<Algorithm> getAlgorithms() {
        List<Algorithm> algos = new ArrayList<>();
        Map<String, Object> algoConfigs = (Map<String, Object>) config.get("algorithms");
        for (String algoName : algoConfigs.keySet()) {
            Object val = algoConfigs.get(algoName);
            if (val instanceof List) {
                for (Map<String, Object> algoConfig : (List<Map<String, Object>>) val) {
                    getAlgorithm(algoName, algoConfig, algos);
                }
            } else {
                getAlgorithm(algoName, (Map<String, Object>) val, algos);
            }
        }
        return algos;
    }

    @SuppressWarnings("unchecked")
    public List<String> getInputs() {
        return (List<String>) config.get("inputs");
    }

    public String getOutput() {
        return (String) config.get("output");
    }

    public void writeTo(File output, Map<Pair<Algorithm, String>, Result> results) {
        Map<String, Map<String, Map<String, Object>>> transformed = new HashMap<>();
        for (Pair<Algorithm, String> input : results.keySet()) {
            Result result = results.get(input);
            Map<String, Map<String, Object>> inputResults;
            String algoName = input.getFirst().getSolutionIdentifier();
            Map<String, Object> algoResults;
            // ensure input exists in transformed map
            if (!transformed.containsKey(input.getSecond())) {
                inputResults = new HashMap<>();
                transformed.put(input.getSecond(), inputResults);
            } else {
                inputResults = transformed.get(input.getSecond());
            }
            // ensure algorithm exists in transformed map
            if (!inputResults.containsKey(algoName)) {
                algoResults = new HashMap<>();
                inputResults.put(algoName, algoResults);
            } else {
                algoResults = inputResults.get(algoName);
            }
            // put results in map
            algoResults.put("runtime", result.runningTimeMs);
            algoResults.put("intersections", result.numIntersections);
            algoResults.put("edgeLength", result.totalEdgeLength);
        }

        try {
            YAML.dump(transformed, new FileWriter(output));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @SuppressWarnings("unchecked")
    private void getAlgorithm(String algoName, Map<String, Object> algoConfig, List<Algorithm> algos) {
        Object algoObj = null;
        Class<?> algoClass = null;
        try {
            algoClass = Class.forName("rbptrees.algo." + algoName);
            // instantiate the class
            if (algoConfig != null && algoConfig.containsKey("constructor")) {
                List<Object> newParams = (List<Object>) algoConfig.get("constructor");
                Constructor<?> cons = algoClass.getConstructor(
                        newParams.stream().map(p -> getRealClass(p)).toArray(Class[]::new));
                algoObj = cons.newInstance(newParams.toArray());
            } else {
                algoObj = algoClass.newInstance();
            }

            // ensure we loaded an algorithm
            if (!(algoObj instanceof Algorithm)) {
                System.err.println(String.format("algorithm '%s' is not an Algorithm", algoName));
                return;
            }
        } catch (ClassNotFoundException e) {
            // if this class does not exist, skip it and only print a warning
            System.err.println(String.format("cannot find algorithm '%s'", algoName));
        } catch (InstantiationException | IllegalAccessException |
                IllegalArgumentException | InvocationTargetException |
                NoSuchMethodException | SecurityException e) {
            System.err.println(String.format("cannot instantiate algorithm '%s'", algoName));
            System.err.println(e.getMessage());
        }
        if (algoObj == null) {
            return;
        }

        // set other parameters, if applicable
        Algorithm algo = (Algorithm) algoObj;
        if (algoConfig != null && algoConfig.containsKey("parameters")) {
            Map<String, Object> params = (Map<String, Object>) algoConfig.get("parameters");
            for (String param : params.keySet()) {
                setAlgorithmParameter(algo, algoClass, param, params.get(param));
            }
        }

        // add algorithm to return value list
        algos.add(algo);
    }

    @SuppressWarnings("unchecked")
    private Object getEnumValue(String className, String enumValue) {
        try {
            @SuppressWarnings("rawtypes")
            Class<Enum> klass = (Class<Enum>) Class.forName("rbptrees.algo." + className);
            return Enum.valueOf(klass, enumValue);
        } catch (ClassNotFoundException | ClassCastException e) {
            System.err.println(String.format("cannot find enum '%s#%s'",
                    className, enumValue));
            return null;
        }
    }

    private Class<?> getRealClass(Object obj) {
        Class<?> klass = obj.getClass();
        // for primitive types, return the primitive class
        for (Field field : klass.getFields()) {
            if (field.getName().equals("TYPE")) {
                try {
                    return (Class<?>) field.get(null);
                } catch (IllegalArgumentException | IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
        return klass;
    }

    private void setAlgorithmParameter(Algorithm algo, Class<?> algoClass,
            String param, Object value) {
        String methodName = "set" + Character.toUpperCase(param.charAt(0)) +
                param.substring(1);
        if (value instanceof String && ((String) value).indexOf('#') >= 0) {
            String[] split = ((String) value).split("#");
            if (split.length == 2) {
                value = getEnumValue(split[0], split[1]);
            }
        }
        try {
            Method m = algoClass.getMethod(methodName, getRealClass(value));
            m.invoke(algo, value);
        } catch (NoSuchMethodException | SecurityException |
                IllegalAccessException | IllegalArgumentException |
                InvocationTargetException e) {
            boolean success = false;
            for (Method m : algoClass.getMethods()) {
                if (m.getName().equals(methodName)) {
                    try {
                        m.invoke(algo, value);
                        success = true;
                    } catch (IllegalAccessException | IllegalArgumentException |
                            InvocationTargetException e2) {
                        continue;
                    }
                }
            }
            if (!success) {
                System.err.println(String.format("could not set parameter %s "
                        + "on algorithm %s", param, algo.getName()));
            }
        }
    }

}
