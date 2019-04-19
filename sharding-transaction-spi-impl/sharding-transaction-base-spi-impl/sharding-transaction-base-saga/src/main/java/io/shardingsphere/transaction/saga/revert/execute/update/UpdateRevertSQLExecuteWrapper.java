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

package io.shardingsphere.transaction.saga.revert.execute.update;

import com.google.common.base.Optional;
import io.shardingsphere.transaction.saga.revert.engine.RevertSQLUnit;
import io.shardingsphere.transaction.saga.revert.execute.RevertSQLExecuteWrapper;
import io.shardingsphere.transaction.saga.revert.snapshot.DMLSnapshotAccessor;
import io.shardingsphere.transaction.saga.revert.snapshot.statement.UpdateSnapshotSQLStatement;
import org.apache.shardingsphere.core.parse.antlr.sql.statement.dml.UpdateStatement;
import org.apache.shardingsphere.core.parse.old.lexer.token.DefaultKeyword;
import org.apache.shardingsphere.core.parse.old.parser.context.condition.Column;
import org.apache.shardingsphere.core.parse.old.parser.expression.SQLExpression;
import org.apache.shardingsphere.core.parse.old.parser.expression.SQLNumberExpression;
import org.apache.shardingsphere.core.parse.old.parser.expression.SQLPlaceholderExpression;
import org.apache.shardingsphere.core.parse.old.parser.expression.SQLTextExpression;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Update revert SQL execute wrapper.
 *
 * @author duhongjun
 * @author zhaojun
 */
public final class UpdateRevertSQLExecuteWrapper implements RevertSQLExecuteWrapper {
    
    private final DMLSnapshotAccessor snapshotDataAccessor;
    
    private UpdateRevertSQLContext revertSQLContext;
    
    public UpdateRevertSQLExecuteWrapper(final DMLSnapshotAccessor snapshotDataAccessor) throws SQLException {
        this.snapshotDataAccessor = snapshotDataAccessor;
        UpdateSnapshotSQLStatement snapshotSQLStatement = (UpdateSnapshotSQLStatement) snapshotDataAccessor.getSnapshotSQLStatement();
        revertSQLContext = createRevertSQLContext(snapshotSQLStatement.getActualTableName(), snapshotSQLStatement.getUpdateStatement(),
            snapshotSQLStatement.getActualSQLParameters(), snapshotSQLStatement.getPrimaryKeyColumns());
    }
    
    private UpdateRevertSQLContext createRevertSQLContext(final String actualTableName, final UpdateStatement updateStatement,
                                                          final List<Object> actualSQLParameters, final List<String> primaryKeyColumns) throws SQLException {
        List<Map<String, Object>> undoData = snapshotDataAccessor.queryUndoData();
        Map<String, Object> updateColumns = new LinkedHashMap<>();
        for (Entry<Column, SQLExpression> entry : updateStatement.getAssignments().entrySet()) {
            if (entry.getValue() instanceof SQLPlaceholderExpression) {
                updateColumns.put(entry.getKey().getName(), actualSQLParameters.get(((SQLPlaceholderExpression) entry.getValue()).getIndex()));
            } else if (entry.getValue() instanceof SQLTextExpression) {
                updateColumns.put(entry.getKey().getName(), ((SQLTextExpression) entry.getValue()).getText());
            } else if (entry.getValue() instanceof SQLNumberExpression) {
                updateColumns.put(entry.getKey().getName(), ((SQLNumberExpression) entry.getValue()).getNumber());
            }
        }
        return new UpdateRevertSQLContext(actualTableName, undoData, updateColumns, primaryKeyColumns, actualSQLParameters);
    }
    
    @Override
    public Optional<RevertSQLUnit> generateRevertSQL() {
        if (revertSQLContext.getUndoData().isEmpty()) {
            return Optional.absent();
        }
        StringBuilder builder = new StringBuilder();
        builder.append(DefaultKeyword.UPDATE).append(" ");
        builder.append(revertSQLContext.getActualTable()).append(" ");
        builder.append(DefaultKeyword.SET).append(" ");
        int pos = 0;
        int size = revertSQLContext.getUpdateColumns().size();
        for (String updateColumn : revertSQLContext.getUpdateColumns().keySet()) {
            builder.append(updateColumn).append(" = ?");
            if (pos < size - 1) {
                builder.append(",");
            }
            pos++;
        }
        builder.append(" ").append(DefaultKeyword.WHERE).append(" ");
        return Optional.of(fillWhereWithKeys(revertSQLContext, builder));
    }
    
    private RevertSQLUnit fillWhereWithKeys(final UpdateRevertSQLContext revertSQLContext, final StringBuilder builder) {
        int pos = 0;
        for (String key : revertSQLContext.getPrimaryKeyColumns()) {
            if (pos > 0) {
                builder.append(" ").append(DefaultKeyword.AND).append(" ");
            }
            builder.append(key).append(" = ? ");
            pos++;
        }
        RevertSQLUnit result = new RevertSQLUnit(builder.toString());
        for (Map<String, Object> each : revertSQLContext.getUndoData()) {
            List<Object> eachSQLParams = new LinkedList<>();
            result.getRevertParams().add(eachSQLParams);
            for (String updateColumn : revertSQLContext.getUpdateColumns().keySet()) {
                eachSQLParams.add(each.get(updateColumn.toLowerCase()));
            }
            for (String key : revertSQLContext.getPrimaryKeyColumns()) {
                Object value = revertSQLContext.getUpdateColumns().get(key);
                if (null != value) {
                    eachSQLParams.add(value);
                } else {
                    eachSQLParams.add(each.get(key));
                }
            }
        }
        return result;
    }
}