package com.kkpp.core.credit.service;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {

    String upload(String directory, MultipartFile file);

    void delete(String fileUrl);
}
