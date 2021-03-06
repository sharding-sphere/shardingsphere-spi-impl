/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opensharding.transaction.base.hook.revert.executor;

import io.opensharding.transaction.base.hook.revert.executor.insert.InsertSQLRevertContext;
import org.apache.shardingsphere.core.optimize.api.segment.InsertValue;
import org.apache.shardingsphere.core.optimize.sharding.statement.dml.ShardingInsertOptimizedStatement;
import org.apache.shardingsphere.core.parse.sql.segment.dml.expr.ExpressionSegment;
import org.apache.shardingsphere.core.parse.sql.segment.dml.expr.simple.ParameterMarkerExpressionSegment;
import org.apache.shardingsphere.core.rule.DataNode;
import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class InsertSQLRevertContextTest {
    
    @Mock
    private ShardingInsertOptimizedStatement shardingInsertOptimizedStatement;
    
    private List<String> primaryKeys = new LinkedList<>();
    
    private String dataSourceName;
    
    private String tableName;
    
    private int shard;
    
    @Before
    public void setUp() {
        dataSourceName = "ds_0";
        tableName = "t_order_0";
        shard = 10;
        when(shardingInsertOptimizedStatement.getColumnNames()).thenReturn(mockColumnNames("order_id", "user_id", "status"));
        when(shardingInsertOptimizedStatement.getInsertValues()).thenReturn(mockInsertOptimizeResult("order_id", "user_id", "status"));
    }
    
    private List<InsertValue> mockInsertOptimizeResult(final String... columnNames) {
        List<InsertValue> result = new LinkedList<>();
        for (int i = 1; i <= shard; i++) {
            InsertValue unit = new InsertValue(mockExpressionSegment(columnNames.length), 0, mockParameters(columnNames.length), 0);
            unit.getDataNodes().add(new DataNode(dataSourceName, tableName));
            result.add(unit);
        }
        return result;
    }
    
    private List<ExpressionSegment> mockExpressionSegment(final int length) {
        List<ExpressionSegment> result = new LinkedList<>();
        for (int i = 0; i < length; i++) {
            result.add(mock(ParameterMarkerExpressionSegment.class));
        }
        return result;
    }
    
    private List<String> mockColumnNames(final String... columnNames) {
        List<String> result = new LinkedList<>();
        result.addAll(Arrays.asList(columnNames));
        return result;
    }
    
    private List<Object> mockParameters(final int length) {
        List<Object> result = new LinkedList<>();
        for (int i = 0; i < length; i++) {
            result.add(i);
        }
        return result;
    }
    
    @Test
    public void assertCreateInsertSQLRevertContextWithSinglePrimaryKey() {
        primaryKeys.add("user_id");
        InsertSQLRevertContext sqlRevertContext = new InsertSQLRevertContext(dataSourceName, tableName, primaryKeys, shardingInsertOptimizedStatement);
        assertThat(sqlRevertContext.getPrimaryKeyInsertValues().size(), is(10));
        for (Map<String, Object> each : sqlRevertContext.getPrimaryKeyInsertValues()) {
            assertThat(each.get("user_id"), CoreMatchers.<Object>is(1));
        }
    }
    
    @Test
    public void assertCreateInsertSQLRevertContextWithMultiPrimaryKeys() {
        primaryKeys.add("order_id");
        primaryKeys.add("user_id");
        InsertSQLRevertContext sqlRevertContext = new InsertSQLRevertContext(dataSourceName, tableName, primaryKeys, shardingInsertOptimizedStatement);
        assertThat(sqlRevertContext.getPrimaryKeyInsertValues().size(), is(10));
        for (Map<String, Object> each : sqlRevertContext.getPrimaryKeyInsertValues()) {
            assertThat(each.get("order_id"), CoreMatchers.<Object>is(0));
            assertThat(each.get("user_id"), CoreMatchers.<Object>is(1));
        }
    }
    
    @Test
    public void assertCreateInsertSQLRevertContextWithoutPrimaryKey() {
        InsertSQLRevertContext sqlRevertContext = new InsertSQLRevertContext(dataSourceName, tableName, primaryKeys, shardingInsertOptimizedStatement);
        assertTrue(sqlRevertContext.getPrimaryKeyInsertValues().isEmpty());
    }
}
