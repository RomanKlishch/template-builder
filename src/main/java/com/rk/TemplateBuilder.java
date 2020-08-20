package com.rk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TemplateBuilder {
    private static final String TEMPLATE_DIR = "/webapp/templates";
    private static final Pattern PATTERN_FIND_EXPRESSION = Pattern.compile("(\\$\\{(.*?)\\})");
    private static final Pattern PATTERN_CUT_VALUE = Pattern.compile("(\\w+\\.*\\w*)");
    private static final Pattern PATTERN_FIND_LIST = Pattern.compile("((?<=list )\\w+)");
    private static final Pattern PATTERN_DELETE_TEG_LIST = Pattern.compile("\\.");
    private static final Pattern PATTERN_CUT_TABLE = Pattern.compile("(?<=\\>)([\\s\\S]+?)(?=\\<\\/\\#list)");
    private static TemplateBuilder templateBuilder;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public static TemplateBuilder instance() {
        if (templateBuilder == null)
            templateBuilder = new TemplateBuilder();
        return templateBuilder;
    }

    public String generateTemplate(String path, Map<String, Object> parameters) {
        StringBuilder sb = new StringBuilder();
        String page = readTemplate(path);
        String templateWithList = listGenerator(page, parameters);
        Matcher matcher = PATTERN_FIND_EXPRESSION.matcher(templateWithList);
        while (matcher.find()) {
            String[] expresion = cutValue(matcher.group(1)).split("\\.");
            Object object = parameters.get(expresion[0]);
            if (expresion.length > 1) {
                matcher.appendReplacement(sb, insert(object, expresion[1]));
            } else {
                matcher.appendReplacement(sb, insert(object));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    protected String listGenerator(String template, Map<String, Object> parameters) {
        StringBuilder stringBuilder = new StringBuilder(template);
        Matcher matcher = PATTERN_FIND_LIST.matcher(template);
        while (matcher.find()) {
            String valueFromExpresion = matcher.group(1);
            List<?> objects = (List<?>) parameters.get(valueFromExpresion);
            template = tableGenerator(stringBuilder, objects, matcher.start(1));
        }
//TODO:Написать регулярку для поиска всех тегов с list
//        Matcher matcherDeleteTegList = PATTERN_DELETE_TEG_LIST.matcher(template);
//        StringBuilder builder = new StringBuilder();
//        while (matcherDeleteTegList.find()){
//            matcherDeleteTegList.appendReplacement(builder,"");
//        }

        return template;
    }

    protected String insert(Object object, String fieldName) {
        if (fieldName == null) {
            return String.valueOf(object);
        }
        Class<?> clazz = object.getClass();
        Method[] methods = clazz.getDeclaredMethods();
        for (Method method : methods) {
            if ("get".concat(fieldName).equalsIgnoreCase(method.getName())) {
                try {
                    return String.valueOf(method.invoke(object));
                } catch (IllegalAccessException | InvocationTargetException e) {
                    logger.error("Invoke method - ", e);
                }
            }
        }
        return "";
    }

    protected String insert(Object object) {
        return object == null ? "" : object.toString();
    }

    protected String tableGenerator(StringBuilder template, List<?> objects, int searchStart) {
        Matcher matcher = PATTERN_CUT_TABLE.matcher(template);
        StringBuilder sb = new StringBuilder();

        while (matcher.find(searchStart)) {
            if (objects == null) {
                matcher.appendReplacement(sb, "");
            } else {
                int index = 0;
                String rowTable = matcher.group(1);
                String fullRow = writeRowTable(objects, rowTable, index++);
                matcher.appendReplacement(sb, fullRow);
                for (; index < objects.size(); index++) {

                    fullRow = writeRowTable(objects, rowTable, index);
                    sb.append(fullRow);
                }
            }
            break;
        }
        matcher.appendTail(sb);
        return String.valueOf(sb);
    }

    protected String writeRowTable(List<?> objects, String rowTable, int index) {
        StringBuilder sb = new StringBuilder();
        Matcher matcherFindRow = PATTERN_FIND_EXPRESSION.matcher(rowTable);
        while (matcherFindRow.find()) {
            String[] expresion = cutValue(matcherFindRow.group(1)).split("\\.");

            Object object = objects.get(index);
            if (expresion.length > 1) {
                matcherFindRow.appendReplacement(sb, insert(object, expresion[1]));
            } else {
                matcherFindRow.appendReplacement(sb, insert(object, null));
            }
        }
        matcherFindRow.appendTail(sb);
        return sb.toString();
    }

    protected String cutValue(String expresion) {
        String valueFromExpresion = "";
        Matcher matcher = PATTERN_CUT_VALUE.matcher(expresion);
        while (matcher.find()) {
            valueFromExpresion = matcher.group(1);
        }
        return valueFromExpresion;
    }

    //TODO: getClass().getResourceAsStream(TEMPLATE_DIR.concat(path)) ищет только в скомпилированых класах? Если честно не понятно как это работает и где ищет
    protected String readTemplate(String path) {
        StringBuilder builder = new StringBuilder();
        InputStream stream = getClass().getResourceAsStream(TEMPLATE_DIR.concat(path));
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        } catch (IOException e) {
            logger.error("Read template - ", e);
        }
        return String.valueOf(builder);
    }
}
