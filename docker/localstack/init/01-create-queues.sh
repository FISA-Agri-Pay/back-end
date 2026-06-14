#!/bin/sh
set -e

awslocal sqs create-queue --queue-name credit-payment-requested.fifo --attributes FifoQueue=true,ContentBasedDeduplication=false
awslocal sqs create-queue --queue-name payment-pin-verified.fifo --attributes FifoQueue=true,ContentBasedDeduplication=false
