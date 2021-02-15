package io.openvidu.server.recording;

import com.google.cloud.storage.*;
import io.openvidu.server.config.OpenviduConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


public class GoogleCloudStorageRecordingUploader implements RecordingUploader {

    private static final Logger log = LoggerFactory.getLogger(GoogleCloudStorageRecordingUploader.class);

    @Autowired
    private OpenviduConfig config;

    @Autowired
    private Storage gcpStorage;

    private final Set<String> recordingsBeingCurrentlyUploaded = ConcurrentHashMap.newKeySet();

    @Override
    public void uploadRecording(Recording recording, Runnable successCallback, Runnable errorCallback) {
        log.info("Uploading recording {} for session {}", recording.getId(), recording.getSessionId());
        recordingsBeingCurrentlyUploaded.add(recording.getId());

        String bucketName = config.getGcpStorageBucketName();
        String gcpFilename = recording.getName() + getRecordingFileExtension(recording);
        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucketName, gcpFilename)).build();
        String filePath = getComposedRecordingLocalFilePath(recording);

        try {
            gcpStorage.create(blobInfo, Files.readAllBytes(Paths.get(filePath)));
        } catch (StorageException | IOException e) {
            log.error(e.getMessage(), e);
            log.error("Error uploading recording {} for session {}", recording.getId(), recording.getSessionId());
            errorCallback.run();
            return;
        } finally {
            recordingsBeingCurrentlyUploaded.remove(recording.getId());
        }
        log.info("Recording {} for session {} has been uploaded", recording.getId(), recording.getSessionId());
        successCallback.run();
    }

    private String getComposedRecordingLocalFilePath(Recording recording) {
        // Audio-only recordings are in WEBM file format
        String fileExt = getRecordingFileExtension(recording);
        return config.getOpenViduRecordingPath() + "/" + recording.getId() + "/" + recording.getName() + fileExt;
    }

    private String getRecordingFileExtension(Recording recording) {
        return recording.hasVideo() ? ".mp4" : ".webm";
    }

    // Prevent uploading recordings from being retrieved from REST API with "ready"
    // status. This will force their status back to "stopped" on GET until upload
    // process has finished
    @Override
    public void storeAsUploadingRecording(String recordingId) {
        recordingsBeingCurrentlyUploaded.add(recordingId);
    }

    @Override
    public boolean isBeingUploaded(String recordingId) {
        return recordingsBeingCurrentlyUploaded.contains(recordingId);
    }
}
