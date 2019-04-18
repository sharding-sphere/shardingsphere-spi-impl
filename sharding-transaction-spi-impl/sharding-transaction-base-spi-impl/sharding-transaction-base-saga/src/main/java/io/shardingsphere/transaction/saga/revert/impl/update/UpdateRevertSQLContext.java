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

package io.shardingsphere.transaction.saga.revert.impl.update;

import io.shardingsphere.transaction.saga.revert.impl.RevertSQLContext;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Revert update generator parameter.
 *
 * @author duhongjun
 */
@Getter
public final class UpdateRevertSQLContext extends RevertSQLContext {
    
    private final List<Map<String, Object>> selectSnapshot = new LinkedList<>();
    
    private final Map<String, Object> updateColumns = new LinkedHashMap<>();
    
    private final List<String> keys = new LinkedList<>();
    
    private final List<Object> params = new LinkedList<>();
    
    public UpdateRevertSQLContext(final String tableName, final List<Map<String, Object>> selectSnapshot, final Map<String, Object> updateColumns, final List<String> keys,
                                  final List<Object> params) {
        super(tableName);
        this.selectSnapshot.addAll(selectSnapshot);
        this.updateColumns.putAll(updateColumns);
        this.keys.addAll(keys);
        this.params.addAll(params);
    }
}