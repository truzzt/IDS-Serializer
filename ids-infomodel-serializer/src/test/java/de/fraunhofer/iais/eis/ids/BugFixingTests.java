package de.fraunhofer.iais.eis.ids;

import de.fraunhofer.iais.eis.DataResource;
import de.fraunhofer.iais.eis.Resource;
import de.fraunhofer.iais.eis.ids.jsonld.Serializer;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;


public class BugFixingTests {

    @Test
    public void HeapSizeErrorLargeResource() throws IOException {
        Path filePath = Path.of("src/test/resources/heapSizeErrorLargeResource.json");
        String resourceFromFileAsString = Files.readString(filePath);

        Serializer serializer = new Serializer();
        Resource resource = serializer.deserialize(resourceFromFileAsString,Resource.class);

        Assert.assertTrue(true);

    }

    @Test
    public void HeapSizeErrorSecondLargeResource() throws IOException {
        Path filePath = Path.of("src/test/resources/request3.jsonld");
        String resourceFromFileAsString = Files.readString(filePath);

        Serializer serializer = new Serializer();
        Resource resource = serializer.deserialize(resourceFromFileAsString, DataResource.class);

        Assert.assertTrue(true);

    }
}
