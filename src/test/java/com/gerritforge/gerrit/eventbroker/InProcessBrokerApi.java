// Copyright (C) 2019 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.gerritforge.gerrit.eventbroker;

import static com.gerritforge.gerrit.eventbroker.TopicSubscriber.topicSubscriber;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MapMaker;
import com.google.common.eventbus.EventBus;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.events.Event;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.apache.mina.util.ConcurrentHashSet;
import org.junit.Ignore;

@Ignore
public class InProcessBrokerApi implements BrokerApi {
  private static final FluentLogger log = FluentLogger.forEnclosingClass();
  private final UUID instanceId;
  private final Gson gson;
  private final Map<String, EventBus> eventBusMap;
  private final Set<TopicSubscriber> topicSubscribers;

  public InProcessBrokerApi(UUID instanceId) {
    this.instanceId = instanceId;
    this.gson = new Gson();
    this.eventBusMap = new MapMaker().concurrencyLevel(1).makeMap();
    this.topicSubscribers = new ConcurrentHashSet<>();
  }

  @Override
  public boolean send(String topic, Event event) {
    SourceAwareEventWrapper sourceAwareEvent = toSourceAwareEvent(event);

    EventBus topicEventConsumers = eventBusMap.get(topic);
    try {
      if (topicEventConsumers != null) {
        topicEventConsumers.post(sourceAwareEvent);
      }
    } catch (RuntimeException e) {
      log.atSevere().withCause(e).log();
      return false;
    }
    return true;
  }

  @Override
  public void receiveAsync(String topic, Consumer<SourceAwareEventWrapper> eventConsumer) {
    EventBus topicEventConsumers = eventBusMap.get(topic);
    if (topicEventConsumers == null) {
      topicEventConsumers = new EventBus(topic);
      eventBusMap.put(topic, topicEventConsumers);
    }
    topicEventConsumers.register(eventConsumer);
    topicSubscribers.add(topicSubscriber(topic, eventConsumer));
  }

  @Override
  public Set<TopicSubscriber> topicSubscribers() {
    return ImmutableSet.copyOf(topicSubscribers);
  }

  @Override
  public void disconnect() {
    this.eventBusMap.clear();
  }

  private JsonObject eventToJson(Event event) {
    return gson.toJsonTree(event).getAsJsonObject();
  }

  protected SourceAwareEventWrapper toSourceAwareEvent(Event event) {
    JsonObject body = eventToJson(event);
    return new SourceAwareEventWrapper(
        new SourceAwareEventWrapper.EventHeader(
            instanceId, event.getType(), instanceId, event.eventCreatedOn),
        body);
  }
}
