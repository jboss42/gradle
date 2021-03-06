/*
 * Copyright 2015 the original author or authors.
 *
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
 */

package org.gradle.model.internal.core

import com.google.common.collect.Iterators
import org.gradle.api.DomainObjectCollection
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectFactory
import org.gradle.api.internal.DefaultDomainObjectCollection
import org.gradle.api.internal.DefaultPolymorphicNamedEntityInstantiator
import org.gradle.internal.Actions
import org.gradle.model.internal.manage.instance.ManagedInstance
import spock.lang.Specification

class DomainObjectCollectionBackedModelMapTest extends Specification {
    def "created items get added to the backing collection"() {
        given:
        def backingCollection = Mock(DomainObjectCollection)
        def instantiator = Mock(NamedEntityInstantiator)
        def modelMap = DomainObjectCollectionBackedModelMap.wrap(Item, backingCollection, instantiator, new Named.Namer(), Actions.doNothing())

        when:
        modelMap.create("alma")

        then:
        1 * instantiator.create("alma", Item) >>  { new Item(name: "alma") }
        1 * backingCollection.add({ item -> item.name == "alma" })
        1 * backingCollection.iterator() >> { Iterators.emptyIterator() }
        0 * _
    }

    class Item implements Named {
        String name
    }

    def "is not managed instance when wrapped in groovy decorator"() {
        when:
        def backingCollection = Mock(DomainObjectCollection)
        def instantiator = Mock(NamedEntityInstantiator)
        def modelMap = DomainObjectCollectionBackedModelMap.wrap(Item, backingCollection, instantiator, new Named.Namer(), Actions.doNothing())
        def groovyWrapper = ModelMapGroovyDecorator.wrap(modelMap)

        then:
        !(groovyWrapper instanceof ManagedInstance)
        !(groovyWrapper.withType(Object) instanceof ManagedInstance)
    }

    def "reasonable error message when creating a non-constructible type"() {
        given:
        def backingCollection = new DefaultDomainObjectCollection(Item, []);
        def instantiator = new DefaultPolymorphicNamedEntityInstantiator(Item, "Item")
        instantiator.registerFactory(Item, new NamedDomainObjectFactory<Item>(){
            public Item create(String name) {
                return new Item(name: name)
            }
        })
        def modelMap = new DomainObjectCollectionBackedModelMap(Item, backingCollection, instantiator, new Named.Namer(), Actions.doNothing())

        when:
        modelMap.create("alma", List)

        then:
        def e = thrown InvalidUserDataException
        e.message.contains("Cannot create a List because this type is not known to Item. Known types are: Item")
    }
}
