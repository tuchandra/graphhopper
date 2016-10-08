/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper GmbH licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except in 
 *  compliance with the License. You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.routing.weighting;

/**
 * Turns an unidirectional weight Approximation into a bidirectional consistent one.
 * <p>
 * Ikeda, T., Hsu, M.-Y., Imai, H., Nishimura, S., Shimoura, H., Hashimoto, T., Tenmoku, K., and
 * Mitoh, K. (1994). A fast algorithm for finding better routes by ai search techniques. In VNIS,
 * pages 291–296.
 * <p>
 *
 * @author jansoe
 */
public class ConsistentWeightApproximator {
    private final WeightApproximator uniDirApproximatorForward, uniDirApproximatorReverse;

    public ConsistentWeightApproximator(WeightApproximator weightApprox) {
        uniDirApproximatorForward = weightApprox;
        uniDirApproximatorReverse = weightApprox.duplicate();
    }

    public void setSourceNode(int sourceNode) {
        uniDirApproximatorReverse.setGoalNode(sourceNode);
    }

    public void setGoalNode(int goalNode) {
        uniDirApproximatorForward.setGoalNode(goalNode);
    }

    public double approximate(int fromNode, boolean reverse) {
        double weightApproximation = 0.5
                * (uniDirApproximatorForward.approximate(fromNode) - uniDirApproximatorReverse.approximate(fromNode));

        if (reverse)
            return -weightApproximation;

        return weightApproximation;
    }
}
