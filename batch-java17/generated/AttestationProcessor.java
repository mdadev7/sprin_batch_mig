package com.dev.generateur.processor;

import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import com.dev.generateur.model.AttestationRow;

@Component
public class AttestationProcessor implements ItemProcessor<AttestationRow, AttestationRow> {

    @Override
    public AttestationRow process(AttestationRow item) {
        if (item.getId() == null || item.getId().isBlank()) {
            throw new IllegalArgumentException("id manquant");
        }

        return item;
    }
}
