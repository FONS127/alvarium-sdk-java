/*******************************************************************************
 * Copyright 2023 Dell Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *******************************************************************************/
package com.alvarium.annotators;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.Collator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.apache.logging.log4j.Logger;

import com.alvarium.contracts.Annotation;
import com.alvarium.contracts.AnnotationType;
import com.alvarium.hash.HashProvider;
import com.alvarium.hash.HashProviderFactory;
import com.alvarium.hash.HashType;
import com.alvarium.hash.HashTypeException;
import com.alvarium.sign.SignatureInfo;
import com.alvarium.utils.PropertyBag;

public class ChecksumAnnotator extends AbstractAnnotator implements Annotator {

    final private HashType hash;
    final private SignatureInfo signature;
    private final AnnotationType kind;

    private HashProvider hashProvider;

    protected ChecksumAnnotator(HashType hash, SignatureInfo signature, Logger logger) {
        super(logger);
        this.hash = hash;
        this.signature = signature;
        this.kind = AnnotationType.CHECKSUM;
    }
    
    @Override
    public Annotation execute(PropertyBag ctx, byte[] data) throws AnnotatorException {
        
        this.initHashProvider(this.hash);
        final String key = this.hashProvider.derive(data);

        final ChecksumAnnotatorProps props = ctx.getProperty(
            AnnotationType.CHECKSUM.name(), 
            ChecksumAnnotatorProps.class
        );

        String host = "";
        boolean isSatisfied;
        try{
            host = InetAddress.getLocalHost().getHostName();
            // Get artifact checksum
            final String checksum = this.readFile(props.getChecksumPath());

            // Validate artifact checksum            
            final String artifactHash = this.hashArtifact(props.getArtifactPath());

            isSatisfied = checksum.equals(artifactHash);
        } catch (UnknownHostException | AnnotatorException e) {
            isSatisfied = false;
            //log the error using the logger 
            this.logger.error("Error during ChecksumAnnotator execution: ",e);
        }

        final Annotation annotation = new Annotation(
            key, 
            this.hash, 
            host, 
            this.kind, 
            null, 
            isSatisfied, 
            Instant.now()
        );

        final String annotationSignature = super.signAnnotation(
            this.signature.getPrivateKey(), 
            annotation
        );
        annotation.setSignature(annotationSignature);
        return annotation;
    }
    
    /**
    *  Initializes a hash provider 
    * @return HashProvider
    * @throws AnnotatorException - If hashing algorithm not found, 
    * or if an unknown exception was thrown
    */
    private final void initHashProvider(HashType hashType) throws AnnotatorException {
        try {
             this.hashProvider = new HashProviderFactory().getProvider(hashType);
        } catch (HashTypeException e) {
            throw new AnnotatorException("Hashing algorithm not found, could not hash data or validate checksum", e);
        } catch (Exception e) {
            throw new AnnotatorException("Could not hash data or validate checksum", e);
        }
    }

    /**
     * Reads a file on the local file system
     * @param filePath
     * @return String content of file
     * @throws AnnotatorException - When bad file path or corrupted file given
     */
    private final String readFile(String filePath) throws AnnotatorException {
        final String content;
        try {
          content = Files.readString(
                Paths.get(filePath),
                StandardCharsets.UTF_8
            );
        } catch (IOException e) {
            throw new AnnotatorException("Failed to read file, could not validate checksum", e);
        }
        return content;
    }

    /**
     * Reads and hashes a file on the local file system in in chunks of 8KB 
     * @param filePath
     * @return hash of the file's contents in string format
     * @throws AnnotatorException - When bad file path or corrupted file given
     */
    private final String hashFile(String filePath) throws AnnotatorException {
        try {
            FileInputStream fs = new FileInputStream(filePath);
            final byte[] buffer = new byte[8192];

            int bytesRead = 0;
            while (true) {
                bytesRead = fs.read(buffer);
                if (bytesRead == -1) { // indicates EOF
                    break;
                } else {
                    this.hashProvider.update(buffer, 0, bytesRead);
                }
            }

            fs.close();
        } catch(IOException e) {
            throw new AnnotatorException(
                "Failed to hash artifact, could not validate checksum", 
                e
            );
        }

        return this.hashProvider.getValue();
    }

    /**
     * Recursively gets all files in a directory as a list of absolute paths
     * @param path
     * @return List<String> of all files in directory
     */
    private List<String> getAllFiles(String path) {
        List<String> files = new ArrayList<>();
        File directory = new File(path);

        if (directory.isDirectory()) {
            File[] directoryFiles = directory.listFiles();
            if (directoryFiles != null) {
                for (File file : directoryFiles) {
                    if (file.isFile()) {
                        files.add(file.getAbsolutePath());
                    } else if (file.isDirectory()) {
                        files.addAll(getAllFiles(file.getAbsolutePath()));
                    }
                }
            }
        } else if (directory.isFile()) {
            files.add(directory.getAbsolutePath());
        }
        return files;
    }

    /**
     * Hashes a string
     * @param input
     * @return hash of the string in string format
     */
    private String hashString(String input) {
        return this.hashProvider.derive(input.getBytes());
    }

    /**
     * Computes the hash of all files hashes and their corresponding paths in the specified directory and returns the
     * hash value as a string.
     * @param path the path of the directory to hash
     * @return the hash value of the directory as a string
     * @throws AnnotatorException if an error occurs while hashing the directory
     */
    private String hashArtifact(String path) throws AnnotatorException {
        List<String> filePaths = getAllFiles(path);
        for(int i = 0 ; i<filePaths.size();i++){
            String hashThenPath = hashFile(filePaths.get(i)) + "  " + filePaths.get(i);
            filePaths.set(i, hashThenPath);
        }
        Collections.sort(filePaths, Collator.getInstance(Locale.US));

        String hashesAndFiles = String.join("\n", filePaths) + "\n";
        final String artifactHash = hashString(hashesAndFiles);

        return artifactHash;
     }
}
