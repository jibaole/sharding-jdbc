/*
 * Copyright 1999-2015 dangdang.com.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * </p>
 */

package io.shardingjdbc.orchestration.spring.datasource;

import io.shardingjdbc.core.api.config.MasterSlaveRuleConfiguration;
import io.shardingjdbc.core.api.config.ShardingRuleConfiguration;
import io.shardingjdbc.core.jdbc.core.datasource.MasterSlaveDataSource;
import io.shardingjdbc.core.jdbc.core.datasource.ShardingDataSource;
import io.shardingjdbc.orchestration.api.config.OrchestrationShardingConfiguration;
import io.shardingjdbc.orchestration.internal.config.ConfigurationService;
import io.shardingjdbc.orchestration.internal.state.InstanceStateService;
import io.shardingjdbc.orchestration.reg.base.CoordinatorRegistryCenter;
import lombok.Setter;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

/**
 * Orchestration sharding datasource for spring namespace.
 *
 * @author caohao
 */
public class OrchestrationSpringShardingDataSource extends ShardingDataSource implements ApplicationContextAware {
    
    private final ConfigurationService configurationService;
    
    private final InstanceStateService instanceStateService;
    
    private final OrchestrationShardingConfiguration config;
    
    private final Properties props;
    
    @Setter
    private ApplicationContext applicationContext;
    
    public OrchestrationSpringShardingDataSource(final String name, final boolean overwrite, final CoordinatorRegistryCenter registryCenter, final Map<String, DataSource> dataSourceMap, 
                                                 final ShardingRuleConfiguration shardingRuleConfig, final Properties props) throws SQLException {
        super(shardingRuleConfig.build(dataSourceMap), props);
        configurationService = new ConfigurationService(name, registryCenter);
        instanceStateService = new InstanceStateService(name, registryCenter);
        config = new OrchestrationShardingConfiguration(
                name, overwrite, registryCenter, getActualDataSourceMapAndReviseShardingRuleConfiguration(dataSourceMap, shardingRuleConfig), shardingRuleConfig);
        this.props = props;
    }
    
    /**
     * initial orchestration spring sharding data source.
     */
    public void init() {
        configurationService.persistShardingConfiguration(config, props);
        configurationService.addShardingConfigurationChangeListener(config.getName(), config.getRegistryCenter(), this);
        instanceStateService.addShardingState(this);
    }
    
    private Map<String, DataSource> getActualDataSourceMapAndReviseShardingRuleConfiguration(final Map<String, DataSource> dataSourceMap, final ShardingRuleConfiguration shardingRuleConfig) {
        Map<String, DataSource> result = new HashMap<>();
        for (Entry<String, DataSource> entry : dataSourceMap.entrySet()) {
            if (entry.getValue() instanceof MasterSlaveDataSource) {
                MasterSlaveDataSource masterSlaveDataSource = (MasterSlaveDataSource) entry.getValue();
                result.putAll(masterSlaveDataSource.getAllDataSources());
                shardingRuleConfig.getMasterSlaveRuleConfigs().add(getMasterSlaveRuleConfiguration(masterSlaveDataSource));
            } else {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }
    
    private MasterSlaveRuleConfiguration getMasterSlaveRuleConfiguration(final MasterSlaveDataSource masterSlaveDataSource) {
        MasterSlaveRuleConfiguration result = new MasterSlaveRuleConfiguration();
        result.setName(masterSlaveDataSource.getMasterSlaveRule().getName());
        result.setMasterDataSourceName(masterSlaveDataSource.getMasterSlaveRule().getMasterDataSourceName());
        result.setSlaveDataSourceNames(masterSlaveDataSource.getMasterSlaveRule().getSlaveDataSourceMap().keySet());
        result.setLoadBalanceAlgorithmClassName(masterSlaveDataSource.getMasterSlaveRule().getStrategy().getClass().getName());
        return result;
    }
    
//    @Override
//    public void renew(final ShardingRule newShardingRule, final Properties newProps) throws SQLException {
//        for (Entry<String, DataSource> entry : newShardingRule.getDataSourceMap().entrySet()) {
//            if (entry.getValue() instanceof MasterSlaveDataSource) {
//                for (Entry<String, DataSource> masterSlaveEntry : ((MasterSlaveDataSource) entry.getValue()).getAllDataSources().entrySet()) {
//                    DataSourceBeanUtil.createDataSourceBean(applicationContext, masterSlaveEntry.getKey(), masterSlaveEntry.getValue());
//                }
//            } else {
//                DataSourceBeanUtil.createDataSourceBean(applicationContext, entry.getKey(), entry.getValue());
//            }
//        }
//        super.renew(newShardingRule, newProps);
//    }
}