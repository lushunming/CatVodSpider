package com.github.catvod.bean.tianyi;

public class ShareData {

    private String shareId;
    private String folderId;
    private String sharePwd;
    private String fileId;
    private Integer shareMode;
    private Boolean isFolder;


    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public Integer getShareMode() {
        return shareMode;
    }

    public void setShareMode(Integer shareMode) {
        this.shareMode = shareMode;
    }

    public Boolean getFolder() {
        return isFolder;
    }

    public void setFolder(Boolean folder) {
        isFolder = folder;
    }

    public ShareData(String shareId, String folderId) {
        this.shareId = shareId;
        this.folderId = folderId;
    }

    public String getSharePwd() {
        return sharePwd;
    }

    public void setSharePwd(String sharePwd) {
        this.sharePwd = sharePwd;
    }

    public String getShareId() {
        return shareId;
    }

    public void setShareId(String shareId) {
        this.shareId = shareId;
    }

    public String getFolderId() {
        return folderId;
    }

    public void setFolderId(String folderId) {
        this.folderId = folderId;
    }
}
