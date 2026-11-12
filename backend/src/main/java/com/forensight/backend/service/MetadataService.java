package com.forensight.backend.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.Directory;
import com.drew.metadata.Tag;

import org.springframework.stereotype.Service;

import java.io.File;
import java.util.HashMap;
import java.util.Map;


@Service
public class MetadataService {


    public Map<String,String> extractMetadata(String filePath){

        Map<String,String> metadata = new HashMap<>();

        try{

            Metadata data =
                    ImageMetadataReader.readMetadata(
                            new File(filePath)
                    );


            for(Directory directory : data.getDirectories()){

                for(Tag tag : directory.getTags()){

                    metadata.put(
                            tag.getTagName(),
                            tag.getDescription()
                    );

                }

            }


        }
        catch(Exception e){

            metadata.put(
                    "Error",
                    "Unable to extract metadata"
            );

        }


        return metadata;
    }
}