package com.dev.processor;

import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import com.dev.model.AttestationRow;

@Component
public class AttestationRowProcessor implements ItemProcessor<AttestationRow, AttestationRow> {
    @Override
    public AttestationRow process(AttestationRow item) {
        // TODO : validations métier — lever IllegalArgumentException pour skip
        return item;
    }
}
