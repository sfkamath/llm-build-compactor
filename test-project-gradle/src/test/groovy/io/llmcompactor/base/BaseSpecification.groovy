package io.llmcompactor.base

import spock.lang.Specification

class BaseSpecification extends Specification {

    def "inherited condition test"() {
        given:
        def book = new Book(id: null, title: "Test")

        expect:
        book.id != null
    }

    static class Book {
        Long id
        String title
    }
}
