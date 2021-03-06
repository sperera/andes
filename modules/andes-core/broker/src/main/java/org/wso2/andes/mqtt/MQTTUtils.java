/*
*
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
package org.wso2.andes.mqtt;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dna.mqtt.moquette.proto.messages.AbstractMessage;
import org.wso2.andes.kernel.AndesException;
import org.wso2.andes.kernel.AndesMessageMetadata;
import org.wso2.andes.kernel.AndesMessagePart;
import org.wso2.andes.kernel.MessagingEngine;

import java.nio.ByteBuffer;

/**
 * This class will contain operations such as convertion of message which are taken from the protocol to be complient
 * with the objects expected by Andes, message id generation, construction of meta informaion,
 */
public class MQTTUtils {

    private static Log log = LogFactory.getLog(MQTTUtils.class);
    private static final String MESSAGE_ID = "MessageID";
    private static final String TOPIC = "Topic";
    private static final String DESTINATION = "Destination";
    private static final String PERSISTENCE = "Persistant";
    private static final String MESSAGE_CONTENT_LENGTH = "MessageContentLength";
    //This will be required to be at the initial byte stream the meta data will have since when the message is proccessed
    //back from andes since the message relevency is checked ex :- whether its amqp, mqtt etc
    private static final String MQTT_META_INFO = "\u0002MQTT Protocol v3.1";

    /**
     * The pulished messages will be taken in as a byte stream, the mesage will be transformed into AndesMessagePart as
     * its required by the Andes kernal for processing
     *
     * @param message  the message contents
     * @param messagID the message identifier
     * @return AndesMessagePart which wrapps the message into a Andes kernal complient object
     */
    public static AndesMessagePart convertToAndesMessage(ByteBuffer message, long messagID) {
        AndesMessagePart messageBody = new AndesMessagePart();
        //TODO need to check if this causes any memory leakes, it would be good to transfer the bytestream forward as buffer
        byte[] data = message.array();
        messageBody.setOffSet(0);
        messageBody.setData(data);
        messageBody.setMessageID(messagID);
        messageBody.setDataLength(data.length);
        return messageBody;
    }

    /**
     * Will generate a unique message idneitfier, this id will be unique cluster wide     *
     *
     * @return the unique message identifier
     */
    public static long generateMessageID() {
        return MessagingEngine.getInstance().generateNewMessageId();
    }

    /**
     * The data about the message (meta information) will be constructed at this phase Andes requires the meta data as a
     * byte stream, The method basically collects all the relevant information neccessary to construct the bytes stream
     * and will convert the message into a bytes object
     *
     * @param metaData      the information about the published message
     * @param messageID     the identity of the message
     * @param topic         the topic the message was published
     * @param destination   the definition where the message should be sent to
     * @param persistance   should this message be persisted
     * @param contentLength the lengthe of the message content
     * @return the collective information as a bytes object
     */
    public static byte[] encodeMetaInfo(String metaData, long messageID, boolean topic, String destination,
                                        boolean persistance, int contentLength) {
        byte[] metaInformation;
        String information = metaData + ":" + MESSAGE_ID + "=" + messageID + "," + TOPIC + "=" + topic +
                "," + DESTINATION + "=" + destination + "," + PERSISTENCE + "=" + persistance
                + "," + MESSAGE_CONTENT_LENGTH + "=" + contentLength;
        metaInformation = information.getBytes();
        return metaInformation;
    }

    /**
     * Extract the message information and will generate the object which will be complient with the Andes kernal
     *
     * @param messageID            message identification
     * @param topic                name of topic
     * @param qosLevel             the level of qos the message was published
     * @param messageContentLength the content length of the message
     * @param retain               should this message retain
     * @return the meta information complient with the kernal
     */
    public static AndesMessageMetadata convertToAndesHeader(long messageID, String topic, int qosLevel,
                                                            int messageContentLength, boolean retain) {
        AndesMessageMetadata messageHeader = new AndesMessageMetadata();
        messageHeader.setMessageID(messageID);
        messageHeader.setTopic(true);
        messageHeader.setDestination(topic);
        messageHeader.setPersistent(true);
        messageHeader.setChannelId(1);
        messageHeader.setMessageContentLength(messageContentLength);
        if (log.isDebugEnabled()) {
            log.debug("Message with id " + messageID + " having the topic " + topic + " with QOS" + qosLevel
                    + " and retain flag set to " + retain + " was created");
        }

        byte[] andesMetaData = encodeMetaInfo(MQTT_META_INFO, messageHeader.getMessageID(), messageHeader.isTopic(),
                messageHeader.getDestination(), messageHeader.isPersistent(), messageContentLength);
        messageHeader.setMetadata(andesMetaData);
        return messageHeader;
    }

    /**
     * Will extract out the message content from the meta data object provided, this will be called when a published
     * message is distributed among the subscribers
     *
     * @param metadata the meta object which holds information about the message
     * @return the byte stream of the message
     */
    public static ByteBuffer getContentFromMetaInformation(AndesMessageMetadata metadata) throws AndesException {
        //Need to get the value dynamically
        ByteBuffer message = ByteBuffer.allocate(metadata.getMessageContentLength());
        try {
            //offset value will always be set to 0 since mqtt doesn't support chunking the messsages, always the message
            //will be in the first chunk but in AMQP there will be chunks
            final int mqttOffset = 0;
            AndesMessagePart messagePart = MessagingEngine.getInstance().getContent(metadata.getMessageID(),mqttOffset);
            message.put(messagePart.getData());
        } catch (AndesException e) {
            final String errorMessage = "Error in getting content for message";
            throw new AndesException(errorMessage, e);
        }
        return message;
    }

    /**
     * For each topic there will only be one subscriber connection cluster wide locally there can be multiple chnnels
     * bound. This method will generate a unique id for the subscription created per topic
     *
     * @return the unique identifier
     */
    public static String generateTopicSpecficClientID() {
        final String mqttSubscriptionID = "MQTTAndesSubscriber:";
        return mqttSubscriptionID + String.valueOf(MessagingEngine.getInstance().generateNewMessageId());
    }

    /**
     * Will conver between the types of the QOS to adhere to the conversion of both andes and mqtt protocol
     *
     * @param qos the quality of service level the message should be published/subscribed
     * @return the level which is complient by the mqtt library
     */
    public static AbstractMessage.QOSType getMQTTQOSTypeFromInteger(int qos) {
        AbstractMessage.QOSType message = null;
        switch (qos) {
            case 0:
                message = AbstractMessage.QOSType.MOST_ONE;
                break;
            case 1:
                message = AbstractMessage.QOSType.LEAST_ONE;
                break;
            case 2:
                message = AbstractMessage.QOSType.EXACTLY_ONCE;
                break;
        }

        return message;
    }
}
