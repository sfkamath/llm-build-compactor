package io.llmcompactor.testbed

import spock.lang.Specification

class GroovySpockTest extends Specification {

    def "null pointer exception test"() {
        given:
        def entity = null

        when:
        def result = entity?.name?.contains("test")

        then:
        thrown(NullPointerException)
    }

    def "condition not satisfied example"() {
        given:
        def expected = "value1"
        def actual = "value2"

        expect:
        expected == actual
    }
}
