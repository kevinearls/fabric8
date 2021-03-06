/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fabric8.io.fabric8.workflow.build.correlate;

import io.fabric8.io.fabric8.workflow.build.BuildCorrelationKey;

import java.util.List;

/**
 * Correlates a triggered build with a jBPM process id
 * so that later on when a build completes or fails we can signal
 * the related process instance; or create a new process via a signal
 */
public interface BuildProcessCorrelator {

    /**
     * Associates the build correlation key with the given jBPM process instance ID
     * so that it can be later correlated when we detect a build has finished
     */
    void putBuildProcessInstanceId(BuildCorrelationKey key, long processInstanceId);

    /**
     * Finds the process instance ID for the given build key
     */
    Long findProcessInstanceIdForBuild(BuildCorrelationKey buildKey);
}
