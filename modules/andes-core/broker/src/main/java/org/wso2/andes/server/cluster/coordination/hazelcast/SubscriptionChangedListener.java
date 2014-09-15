/*
*  Copyright (c) 2005-2010, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/

package org.wso2.andes.server.cluster.coordination.hazelcast;

import com.hazelcast.core.Message;
import com.hazelcast.core.MessageListener;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.andes.server.ClusterResourceHolder;
import org.wso2.andes.server.cluster.coordination.SubscriptionNotification;

/**
 * This listener class is triggered when any subscription change (Subscriber added, subscriber deleted
 * or subscriber disconnected) is happened.
 */
public class SubscriptionChangedListener implements MessageListener {
    private static Log log = LogFactory.getLog(SubscriptionChangedListener.class);

    /**
     * This method is triggered when a subscription is changed in clustered environment.
     *
     * @param message contains the SubscriptionNotification
     */
    @Override
    public void onMessage(Message message) {
        SubscriptionNotification subscriptionNotification = (SubscriptionNotification) message.getMessageObject();
        log.info("Handling cluster gossip: received a subscriber changed notification. Queue:" + subscriptionNotification.getAndesQueue().queueName);
        ClusterResourceHolder.getInstance().getSubscriptionCoordinationManager().handleClusterSubscriptionChange(subscriptionNotification);
    }
}
