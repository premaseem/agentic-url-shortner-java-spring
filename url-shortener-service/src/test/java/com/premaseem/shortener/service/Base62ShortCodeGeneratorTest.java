package com.premaseem.shortener.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Base62ShortCodeGeneratorTest {

    private final Base62ShortCodeGenerator generator = new Base62ShortCodeGenerator();

    @Test
    void generatesCodeOfExpectedLengthAndAlphabet() {
        String code = generator.generate();

        assertThat(code).hasSize(7);
        assertThat(code).matches("[0-9A-Za-z]+");
    }

    @Test
    void generatesDifferentCodesAcrossCalls() {
        String first = generator.generate();
        String second = generator.generate();

        assertThat(first).isNotEqualTo(second);
    }
}
