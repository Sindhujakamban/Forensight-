package com.forensight.backend.model;


import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;


@Document(collection = "evidence")
public class Evidence {


    @Id
    private String id;


    private String caseId;


    private String fileName;


    private String fileType;


    private long fileSize;


    private String filePath;


    // ELA heatmap image path
    private String elaPath;

    // Native TruFor artifacts generated from the model NPZ output.
    private String truforPath;
    private String truforConfidencePath;


    // NEW: EXIF Metadata
    private Map<String,String> metadata;


    private LocalDateTime uploadedAt;




    public Evidence() {
    }





    public Evidence(String caseId,
                    String fileName,
                    String fileType,
                    long fileSize,
                    String filePath,
                    LocalDateTime uploadedAt) {


        this.caseId = caseId;
        this.fileName = fileName;
        this.fileType = fileType;
        this.fileSize = fileSize;
        this.filePath = filePath;
        this.uploadedAt = uploadedAt;
    }






    public String getId() {
        return id;
    }



    public void setId(String id) {
        this.id = id;
    }






    public String getCaseId() {
        return caseId;
    }



    public void setCaseId(String caseId) {
        this.caseId = caseId;
    }






    public String getFileName() {
        return fileName;
    }



    public void setFileName(String fileName) {
        this.fileName = fileName;
    }






    public String getFileType() {
        return fileType;
    }



    public void setFileType(String fileType) {
        this.fileType = fileType;
    }






    public long getFileSize() {
        return fileSize;
    }



    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }






    @JsonIgnore
    public String getFilePath() {
        return filePath;
    }



    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }






    // ELA PATH


    @JsonIgnore
    public String getElaPath() {
        return elaPath;
    }



    public void setElaPath(String elaPath) {
        this.elaPath = elaPath;
    }

    @JsonIgnore
    public String getTruforPath() { return truforPath; }
    public void setTruforPath(String truforPath) { this.truforPath = truforPath; }
    @JsonIgnore
    public String getTruforConfidencePath() { return truforConfidencePath; }
    public void setTruforConfidencePath(String truforConfidencePath) { this.truforConfidencePath = truforConfidencePath; }






    // METADATA METHODS


    public Map<String,String> getMetadata() {
        return metadata;
    }



    public void setMetadata(Map<String,String> metadata) {
        this.metadata = metadata;
    }







    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }



    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

}