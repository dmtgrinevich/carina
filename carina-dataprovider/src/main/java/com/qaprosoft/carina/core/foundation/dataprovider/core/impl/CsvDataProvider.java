/*******************************************************************************
 * Copyright 2020-2022 Zebrunner Inc (https://www.zebrunner.com).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package com.qaprosoft.carina.core.foundation.dataprovider.core.impl;

import java.io.FileReader;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestContext;
import org.testng.ITestNGMethod;

import com.qaprosoft.carina.core.foundation.dataprovider.annotations.CsvDataSourceParameters;
import com.qaprosoft.carina.core.foundation.dataprovider.core.groupping.GroupByMapper;
import com.qaprosoft.carina.core.foundation.dataprovider.parser.DSBean;
import com.zebrunner.carina.utils.ParameterGenerator;

import au.com.bytecode.opencsv.CSVReader;

/**
 * Created by Patotsky on 16.12.2014.
 */
public class CsvDataProvider extends BaseDataProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private Map<String, Integer> mapper = new HashMap<String, Integer>();

    private String executeColumn;
    private String executeValue;
    private String testRailColumn;
    private String testMethodColumn;
    private String testMethodOwnerColumn;
    private String bugColumn;

    @SuppressWarnings("unchecked")
    @Override
    public Object[][] getDataProvider(Annotation annotation, ITestContext context, ITestNGMethod testMethod) {
        CsvDataSourceParameters parameters = (CsvDataSourceParameters) annotation;
        DSBean dsBean = new DSBean(parameters, context.getCurrentXmlTest().getAllParameters());

        char separator, quote;
        separator = parameters.separator();
        quote = parameters.quote();

        executeColumn = dsBean.getExecuteColumn();
        executeValue = dsBean.getExecuteValue();

        testRailColumn = parameters.testRailColumn();

        if (!parameters.qTestColumn().isEmpty() && testRailColumn.isEmpty())
            testRailColumn = parameters.qTestColumn();

        testMethodColumn = parameters.testMethodColumn();
        testMethodOwnerColumn = parameters.testMethodOwnerColumn();
        bugColumn = parameters.bugColumn();

        argsList = dsBean.getArgs();
        staticArgsList = dsBean.getStaticArgs();

        String groupByParameter = parameters.groupColumn();
        if (!groupByParameter.isEmpty()) {
            GroupByMapper.getInstanceInt().add(argsList.indexOf(groupByParameter));
            GroupByMapper.getInstanceStrings().add(groupByParameter);
        }

        if (parameters.dsArgs().isEmpty()) {
            GroupByMapper.setIsHashMapped(true);
        }
        CSVReader reader;
        List<String[]> list = new ArrayList<String[]>();

        try {
            String csvFile = ClassLoader.getSystemResource(dsBean.getDsFile()).getFile();
            reader = new CSVReader(new FileReader(csvFile), separator, quote);
            list = reader.readAll();
        } catch (IOException e) {
            LOGGER.error("Unable to read data from CSV DataProvider", e);
        }

        if (list.size() == 0) {
            throw new RuntimeException("Unable to retrieve data from CSV DataProvider! Verify separator and quote settings.");
        }
        List<String> headers = Arrays.asList(list.get(0));
        list.remove(0);

        List<Map<String, String>> dsData = initData(headers, list);
        // exclude those lines which don't satisfy executeColumn/executeValue filter
        for (int i = 0; i < dsData.size(); i++) {
            Map<String, String> row = dsData.get(i);
            if (!row.get(dsBean.getExecuteColumn()).equalsIgnoreCase(executeValue)) {
                dsData.remove(i);
                i--;
            }
        }

        int listSize = list.size();

        int width = 0;
        if (argsList.size() == 0) {
            // first element is dynamic HashMap<String, String>
            width = staticArgsList.size() + 1;
        } else {
            width = argsList.size() + staticArgsList.size();
        }

        Object[][] args = new Object[listSize][width];
        int rowIndex = 0;
        for (Map<String, String> row : dsData) {

            if (argsList.size() == 0) {
                for (Map.Entry<String, String> entry : row.entrySet()) {
                    if (entry == null)
                        continue;

                    String value = entry.getValue();
                    if (value == null)
                        continue;

                    Object param = ParameterGenerator.process(value);
                    if (param == null)
                        continue;

                    String newValue = param.toString();
                    if (!value.equals(newValue)) {
                        entry.setValue(newValue);
                    }
                }
                args[rowIndex][0] = row;
                for (int i = 0; i < staticArgsList.size(); i++) {
                    args[rowIndex][i + 1] = getStaticParam(staticArgsList.get(i), context, dsBean);
                }
            } else {
                int i;
                for (i = 0; i < argsList.size(); i++) {
                    args[rowIndex][i] = ParameterGenerator
                            .process(row.get(argsList.get(i)));
                }

                for (int j = 0; j < staticArgsList.size(); j++) {
                    args[rowIndex][i + j] = getStaticParam(staticArgsList.get(j), context, dsBean);
                }
            }

            tuidMap.put(hash(args[rowIndex], testMethod), getValueFromRow(row, dsBean.getUidArgs()));

            if (!testMethodColumn.isEmpty()) {
                String testNameOverride = getValueFromRow(row, List.of(testMethodColumn));
                if (!testNameOverride.isEmpty()) {
                    testColumnNamesMap.put(hash(args[rowIndex], testMethod), getValueFromRow(row, testMethodColumn));
                }
            }

            // add testMethoOwner from xls datasource to special hashMap
            addValueToSpecialMap(testMethodOwnerArgsMap, testMethodOwnerColumn, String.valueOf(Arrays.hashCode(args[rowIndex])), row);

            // add testrails cases from xls datasource to special hashMap
            addValueToSpecialMap(testRailsArgsMap, testRailColumn, String.valueOf(Arrays.hashCode(args[rowIndex])), row);

            rowIndex++;
        }

        return args;
    }

    private List<Map<String, String>> initData(List<String> headers, List<String[]> values) {
        List<Map<String, String>> list = new ArrayList<>();

        for (int i = 0; i < values.size(); i++) {
            Map<String, String> map = new HashMap<>();
            list.add(map);
            for (int j = 0; j < headers.size(); j++) {
                map.put(headers.get(j), values.get(i)[j]);
            }
        }

        return list;
    }
}
