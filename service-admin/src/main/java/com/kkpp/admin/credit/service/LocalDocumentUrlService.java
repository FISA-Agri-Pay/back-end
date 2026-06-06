package com.kkpp.admin.credit.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("local")
public class LocalDocumentUrlService implements DocumentUrlService {

    @Override
    public String resolve(String s3Key) {
        return s3Key;
    }
}