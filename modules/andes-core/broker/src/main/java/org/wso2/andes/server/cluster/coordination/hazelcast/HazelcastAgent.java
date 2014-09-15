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

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.ITopic;
import com.hazelcast.core.IdGenerator;
import com.hazelcast.core.Member;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.andes.server.cluster.coordination.CoordinationConstants;
import org.wso2.andes.server.cluster.coordination.SubscriptionNotification;

import java.util.*;

/**
 * This is a singleton class, which contains all Hazelcast related operations.
 */
public class HazelcastAgent {
    private static Log log = LogFactory.getLog(HazelcastAgent.class);

    /**
     * Singleton HazelcastAgent Instance.
     */
    private static HazelcastAgent hazelcastAgentInstance = new HazelcastAgent();

    /**
     * Hazelcast instance exposed by Carbon.
     */
    private HazelcastInstance hazelcastInstance;

    /**
     * Distributed topic to communicate subscription change notifications among cluster nodes.
     */
    private ITopic<SubscriptionNotification> subscriptionChangedNotifierChannel;

    /**
     * Distributed topic to communicate queue purge notifications among cluster nodes.
     */
    private ITopic queueChangedNotifierChannel;

    /**
     * Unique ID generated to represent the node.
     * This ID is used when generating message IDs.
     */
    private int uniqueIdOfLocalMember;

    /**
     * Get singleton HazelcastAgent.
     *
     * @return HazelcastAgent
     */
    public static HazelcastAgent getInstance() {
        return hazelcastAgentInstance;
    }

    /**
     * Initialize HazelcastAgent instance.
     *
     * @param hazelcastInstance obtained hazelcastInstance from the OSGI service
     */
    @SuppressWarnings("unchecked")
    public void init(HazelcastInstance hazelcastInstance) {
        log.info("Initializing Hazelcast Agent");
        this.hazelcastInstance = hazelcastInstance;
        this.hazelcastInstance.getCluster().addMembershipListener(new AndesMembershipListener());

        // Initialize the ITopic to notify subscription change events and bind the listener to the Topic
        this.subscriptionChangedNotifierChannel = this.hazelcastInstance.getTopic(
                CoordinationConstants.HAZELCAST_SUBSCRIPTION_CHANGED_NOTIFIER_TOPIC_NAME);
        this.subscriptionChangedNotifierChannel.addMessageListener(new SubscriptionChangedListener());

        // Initialize the ITopic to notify queue purge events and bind the listener to the Topic
        this.queueChangedNotifierChannel = this.hazelcastInstance.getTopic(
                CoordinationConstants.HAZELCAST_QUEUE_CHANGED_NOTIFIER_TOPIC_NAME);
        this.queueChangedNotifierChannel.addMessageListener(new QueueChangedListener());

        IdGenerator idGenerator = hazelcastInstance.getIdGenerator(CoordinationConstants.HAZELCAST_ID_GENERATOR_NAME);
        this.uniqueIdOfLocalMember = (int) idGenerator.newId();

        if (log.isDebugEnabled()) {
            log.debug("Unique ID generation for message ID generation:" + uniqueIdOfLocalMember);
        }

        log.info("Successfully initialized Hazelcast Agent");
    }

    /**
     * Node ID is generated in the format of "NODE/<host IP>_<Node UUID>"
     *
     * @return NodeId of the local node
     */
    public String getNodeId() {
        Member localMember = hazelcastInstance.getCluster().getLocalMember();
        return CoordinationConstants.NODE_NAME_PREFIX +
                localMember.getInetSocketAddress().getAddress() +
                "_" +
                localMember.getUuid();
    }

    /**
     * All nodes of the cluster are returned as a Set of Members
     *
     * @return all nodes of the cluster
     */
    public Set<Member> getAllClusterMembers() {
        return hazelcastInstance.getCluster().getMembers();
    }

    /**
     * Get node IDs of all nodes available in the cluster.
     *
     * @return List of node IDs.
     */
    public List<String> getMembersNodeIDs() {
        Set<Member> members = this.getAllClusterMembers();
        List<String> nodeIDList = new ArrayList<String>();
        for (Member member : members) {
            nodeIDList.add(CoordinationConstants.NODE_NAME_PREFIX +
                    member.getInetSocketAddress().getAddress() +
                    "_" +
                    member.getUuid());
        }

        return nodeIDList;
    }

    /**
     * Get local node.
     *
     * @return local node as a Member.
     */
    public Member getLocalMember() {
        return hazelcastInstance.getCluster().getLocalMember();
    }

    /**
     * Get number of nodes in the cluster.
     *
     * @return number of nodes.
     */
    public int getClusterSize() {
        return hazelcastInstance.getCluster().getMembers().size();
    }

    /**
     * Get unique ID to represent local member.
     *
     * @return unique ID assigned for the local node.
     */
    public int getUniqueIdForNode() {
        return uniqueIdOfLocalMember;
    }

    /**
     * Get node ID of the given node.
     *
     * @param node node
     * @return node ID
     */
    public String getIdOfNode(Member node) {
        return CoordinationConstants.NODE_NAME_PREFIX +
                node.getInetSocketAddress().getAddress() +
                "_" +
                node.getUuid();
    }

    /**
     * Each member of the cluster is given an unique UUID and here the UUIDs of all nodes are sorted
     * and the index of the belonging UUID of the given node is returned.
     *
     * @param node node
     * @return the index given to the node according to its UUID
     */
    public int getIndexOfNode(Member node) {
        TreeSet<String> membersUniqueRepresentations = new TreeSet<String>();
        for (Member member : this.getAllClusterMembers()) {
            membersUniqueRepresentations.add(member.getUuid());
        }

        return membersUniqueRepresentations.headSet(node.getUuid()).size();
    }

    /**
     * Get the index where the local node is placed when all the cluster nodes are sorted according to their UUID.
     *
     * @return index given to the local node according to its UUID
     */
    public int getIndexOfLocalNode() {
        return this.getIndexOfNode(this.getLocalMember());
    }

    /**
     * Send cluster wide subscription change notification.
     *
     * @param subscriptionNotification notification
     */
    public void notifySubscriberChanged(SubscriptionNotification subscriptionNotification) {
        log.info("Handling cluster gossip: Sending subscriber changed notification to cluster...");
        this.subscriptionChangedNotifierChannel.publish(subscriptionNotification);
    }
}
