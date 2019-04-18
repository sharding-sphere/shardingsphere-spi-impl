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

package io.shardingsphere.transaction.saga.revert;

import io.shardingsphere.transaction.saga.context.SagaLogicSQLTransaction;
import io.shardingsphere.transaction.saga.revert.api.RevertSQLEngine;
import io.shardingsphere.transaction.saga.revert.api.RevertSQLExecuteWrapper;
import io.shardingsphere.transaction.saga.revert.impl.DMLRevertSQLEngine;
import io.shardingsphere.transaction.saga.revert.impl.delete.DeleteRevertSQLExecuteWrapper;
import io.shardingsphere.transaction.saga.revert.impl.insert.InsertRevertSQLExecuteWrapper;
import io.shardingsphere.transaction.saga.revert.impl.update.UpdateRevertSQLExecuteWrapper;
import org.apache.shardingsphere.core.exception.ShardingException;
import org.apache.shardingsphere.core.metadata.table.ColumnMetaData;
import org.apache.shardingsphere.core.metadata.table.TableMetaData;
import org.apache.shardingsphere.core.parse.antlr.sql.statement.SQLStatement;
import org.apache.shardingsphere.core.parse.antlr.sql.statement.dml.DeleteStatement;
import org.apache.shardingsphere.core.parse.antlr.sql.statement.dml.InsertStatement;
import org.apache.shardingsphere.core.parse.antlr.sql.statement.dml.UpdateStatement;
import org.apache.shardingsphere.core.route.RouteUnit;
import org.apache.shardingsphere.core.route.SQLRouteResult;
import org.apache.shardingsphere.core.route.type.RoutingTable;
import org.apache.shardingsphere.core.route.type.TableUnit;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

/**
 * Revert SQL engine factory.
 *
 * @author duhongjun
 * @author zhaojun
 */
public final class SagaRevertSQLEngineFactory {
    
    /**
     * Create new revert SQL engine.
     *
     * @param logicSQLTransaction logic SQL transaction
     * @param routeUnit route unit
     * @param connectionMap connection map
     * @return revert SQL engine
     */
    public static RevertSQLEngine newInstance(final SagaLogicSQLTransaction logicSQLTransaction, final RouteUnit routeUnit, final ConcurrentMap<String, Connection> connectionMap) {
        SQLStatement sqlStatement = logicSQLTransaction.getSqlRouteResult().getSqlStatement();
        List<Object> parameters = routeUnit.getSqlUnit().getParameters();
        String actualTableName = getActualTableName(logicSQLTransaction.getSqlRouteResult(), routeUnit);
        Connection connection = connectionMap.get(routeUnit.getDataSourceName());
        List<String> primaryKeyColumns = getPrimaryKeyColumns(logicSQLTransaction.getTableMetaData());
        RevertSQLExecuteWrapper revertSQLExecuteWrapper;
        if (sqlStatement instanceof InsertStatement) {
            boolean containGenerateKey = !logicSQLTransaction.getSqlRouteResult().getGeneratedKey().getGeneratedKeys().isEmpty();
            revertSQLExecuteWrapper = new InsertRevertSQLExecuteWrapper(actualTableName, (InsertStatement) sqlStatement, parameters, primaryKeyColumns, containGenerateKey);
        } else if (sqlStatement instanceof DeleteStatement) {
            revertSQLExecuteWrapper = new DeleteRevertSQLExecuteWrapper(actualTableName, (DeleteStatement) sqlStatement, parameters, connection);
        } else if (sqlStatement instanceof UpdateStatement) {
            revertSQLExecuteWrapper = new UpdateRevertSQLExecuteWrapper(actualTableName, (UpdateStatement) sqlStatement, parameters, primaryKeyColumns, connection);
        } else {
            throw new UnsupportedOperationException("unsupported SQL statement");
        }
        return new DMLRevertSQLEngine(revertSQLExecuteWrapper);
    }
    
    private static List<String> getPrimaryKeyColumns(final TableMetaData tableMetaData) {
        List<String> result = new ArrayList<>();
        for (ColumnMetaData each : tableMetaData.getColumns().values()) {
            if (each.isPrimaryKey()) {
                result.add(each.getColumnName());
            }
        }
        if (result.isEmpty()) {
            throw new RuntimeException("Not supported table without primary key");
        }
        return result;
    }
    
    private static String getActualTableName(final SQLRouteResult sqlRouteResult, final RouteUnit routeUnit) {
        for (TableUnit each : sqlRouteResult.getRoutingResult().getTableUnits().getTableUnits()) {
            if (each.getDataSourceName().equalsIgnoreCase(routeUnit.getDataSourceName())) {
                return getAvailableActualTableName(each, sqlRouteResult.getSqlStatement().getTables().getSingleTableName());
            }
        }
        throw new ShardingException(String.format("Could not find actual table name of [%s]", routeUnit));
    }
    
    private static String getAvailableActualTableName(final TableUnit tableUnit, final String logicTableName) {
        for (RoutingTable each : tableUnit.getRoutingTables()) {
            if (each.getLogicTableName().equalsIgnoreCase(logicTableName)) {
                return each.getActualTableName();
            }
        }
        throw new ShardingException(String.format("Could not get available actual table name of [%s]", tableUnit));
    }
}