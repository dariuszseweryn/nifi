package org.apache.nifi.aws.schemaregistry;

import com.amazonaws.services.schemaregistry.deserializers.GlueSchemaRegistryDeserializerDataParser;
import com.amazonaws.services.schemaregistry.utils.AWSSchemaRegistryConstants;
import org.apache.nifi.controller.AbstractControllerService;
import org.apache.nifi.schema.access.SchemaField;
import org.apache.nifi.schema.access.SchemaNotFoundException;
import org.apache.nifi.schemaregistry.services.SchemaReferenceReader;
import org.apache.nifi.serialization.record.SchemaIdentifier;
import org.apache.nifi.stream.io.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class AmazonGlueEncodedSchemaReferenceReader extends AbstractControllerService implements SchemaReferenceReader {

    private final GlueSchemaRegistryDeserializerDataParser deserializerDataParser = GlueSchemaRegistryDeserializerDataParser.getInstance();

    private static final int HEADER_CAPACITY = AWSSchemaRegistryConstants.HEADER_VERSION_BYTE_SIZE
                    + AWSSchemaRegistryConstants.COMPRESSION_BYTE_SIZE
                    + AWSSchemaRegistryConstants.SCHEMA_VERSION_ID_SIZE;

    private static final Set<SchemaField> SUPPLIED_SCHEMA_FIELDS = Set.of(SchemaField.SCHEMA_BRANCH_NAME);

    @Override
    public SchemaIdentifier getSchemaIdentifier(final Map<String, String> variables, final InputStream contentStream) throws SchemaNotFoundException {
        final byte[] header = new byte[HEADER_CAPACITY];
        try {
            StreamUtils.fillBuffer(contentStream, header);
        } catch (final IOException e) {
            throw new SchemaNotFoundException("Failed to read header in first %d bytes from stream".formatted(HEADER_CAPACITY), e);
        }

        final ByteBuffer headerBuffer = ByteBuffer.wrap(header);
        final StringBuilder errorStringBuilder = new StringBuilder();
        if (!deserializerDataParser.isDataCompatible(headerBuffer, errorStringBuilder)) {
            throw new SchemaNotFoundException("Failed to parse Glue Schema Registry header: %s".formatted(errorStringBuilder));
        }
        final UUID schemaVersionId = deserializerDataParser.getSchemaVersionId(headerBuffer);
        return SchemaIdentifier.builder().name(WireFormatSchemaVersionIdUtil.toWireFormatName(schemaVersionId)).build();
    }

    @Override
    public Set<SchemaField> getSuppliedSchemaFields() {
        return SUPPLIED_SCHEMA_FIELDS;
    }
}
