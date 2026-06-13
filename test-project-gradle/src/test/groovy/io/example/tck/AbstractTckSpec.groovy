package io.example.tck

import spock.lang.Specification

class AbstractTckSpec extends Specification {

    def "cross-package inherited test"() {
        given:
        def entity = new Entity(id: null, name: "Test")

        expect:
        entity.id != null
    }

    static class Entity {
        Long id
        String name
    }
}
