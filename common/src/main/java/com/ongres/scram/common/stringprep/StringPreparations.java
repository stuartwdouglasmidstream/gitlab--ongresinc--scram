/*
 * Copyright 2019, OnGres.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 * following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */


package com.ongres.scram.common.stringprep;


import com.ongres.saslprep.SaslPrep;
import com.ongres.scram.common.util.UsAsciiUtils;

import static com.ongres.scram.common.util.Preconditions.checkNotEmpty;

import java.io.IOException;


public enum StringPreparations implements StringPreparation {
    /**
     * Implementation of StringPreparation that performs no preparation.
     * Non US-ASCII characters will produce an exception.
     * Even though the <a href="https://tools.ietf.org/html/rfc5802">[RFC5802]</a> is not very clear about it,
     * this implementation will normalize non-printable US-ASCII characters similarly to what SaslPrep does
     * (i.e., removing them).
     */
    NO_PREPARATION {
        @Override
        protected String doNormalize(String value) throws IllegalArgumentException {
            return UsAsciiUtils.toPrintable(value);
        }
    },
    /**
     * Implementation of StringPreparation that performs preparation.
     * Non US-ASCII characters will produce an exception.
     * Even though the <a href="https://tools.ietf.org/html/rfc5802">[RFC5802]</a> is not very clear about it,
     * this implementation will normalize as SaslPrep does.
     */
    SASL_PREPARATION {
        @Override
        protected String doNormalize(String value) throws IllegalArgumentException {
            try {
                return SaslPrep.saslPrep(value, true);
            } catch (IOException e) {
                throw new IllegalArgumentException("RFC files with the properties of saslprep profile can't be read");
            }
        }
    }
    ;

    protected abstract String doNormalize(String value) throws IllegalArgumentException;

    public String normalize(String value) throws IllegalArgumentException {
        checkNotEmpty(value, "value");

        String normalized = doNormalize(value);

        if(null == normalized || normalized.isEmpty()) {
            throw new IllegalArgumentException("null or empty value after normalization");
        }

        return normalized;
    }
}
