package com.csg.airtel.aaa4j.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BucketDetailsTest {

    @Test
    void testConstructorAndGetters() {
        BucketDetails bucketDetails = new BucketDetails("rule", 1L, "bucket789", 100L, "10-12", null, null, null, 1);


        assertEquals("bucket789", bucketDetails.getBucketId());
        assertEquals(100L, bucketDetails.getCurrentBalance());
    }

    @Test
    void testSetters() {
        BucketDetails bucketDetails = new BucketDetails();

        bucketDetails.setBucketId("newBucket");
        bucketDetails.setCurrentBalance(2000L);

        assertEquals("newBucket", bucketDetails.getBucketId());
        assertEquals(2000L, bucketDetails.getCurrentBalance());
    }

    @Test
    void testNoArgsConstructor() {
        BucketDetails bucketDetails = new BucketDetails();

        assertNull(bucketDetails.getBucketId());
        assertNull(bucketDetails.getCurrentBalance());
    }
}
