package com.twitter.elephantbird.pig.util;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.apache.pig.backend.executionengine.ExecException;
import org.apache.pig.data.Tuple;
import org.apache.pig.data.TupleFactory;

import com.google.common.collect.Sets;
import com.google.protobuf.Message;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message.Builder;

/**
 * This class wraps a protocol buffer message and attempts to delay parsing until individual
 * fields are requested.
 */
public class ProtobufTuple implements Tuple {

  /**
   * Autogenerated by Eclipse.
   */
  private static final long serialVersionUID = 8468589454361280269L;
  
  private final Message msg_;
  private final Descriptor descriptor_;
  private final Tuple realTuple_;
  private final Set<Integer> materializedFieldSet_;
  private final List<FieldDescriptor> fieldDescriptors_;
  private final ProtobufToPig protoConv_;
  private final int protoSize_;
  private boolean ignoreMessage_ = false;

  public ProtobufTuple(Message msg) {
    msg_ = msg;
    descriptor_ = msg.getDescriptorForType();
    fieldDescriptors_ = descriptor_.getFields();
    protoSize_ = fieldDescriptors_.size();
    realTuple_ = TupleFactory.getInstance().newTuple(protoSize_);
    materializedFieldSet_ =  Sets.newHashSetWithExpectedSize(protoSize_);
    protoConv_ = new ProtobufToPig();
  }

  @Override
  public void append(Object obj) {
    realTuple_.append(obj);
  }

  @Override
  public Object get(int idx) throws ExecException {
    if (!ignoreMessage_ && idx < protoSize_ && !materializedFieldSet_.contains(idx)) {
      FieldDescriptor fieldDescriptor = fieldDescriptors_.get(idx);
      Object fieldValue = msg_.getField(fieldDescriptor);
      if (fieldDescriptor.getType() == FieldDescriptor.Type.MESSAGE) {
        realTuple_.set(idx, protoConv_.messageToTuple(fieldDescriptor, fieldValue));
      } else {
        realTuple_.set(idx, protoConv_.singleFieldToTuple(fieldDescriptor, fieldValue));
      }
      materializedFieldSet_.add(idx);
    }
    return realTuple_.get(idx);
  }

  @Override
  public List<Object> getAll() {
    convertAll();
    return realTuple_.getAll();
  }

  @Override
  public long getMemorySize() {
    // The protobuf estimate is obviously inaccurate.
    return msg_.getSerializedSize() + realTuple_.getMemorySize();
  }

  @Override
  public byte getType(int idx) throws ExecException {
    get(idx);
    return realTuple_.getType(idx);
  }

  @Override
  public boolean isNull() {
    return realTuple_.isNull();
  }

  @Override
  public boolean isNull(int idx) throws ExecException {
    get(idx);
    return realTuple_.isNull(idx);
  }

  @Override
  public void reference(Tuple arg0) {
    realTuple_.reference(arg0);
    // Ignore the Message from now on.
    ignoreMessage_ = true;
  }

  @Override
  public void set(int idx, Object val) throws ExecException {
    realTuple_.set(idx, val);
    materializedFieldSet_.add(idx);
  }

  @Override
  public void setNull(boolean isNull) {
    realTuple_.setNull(isNull);    
  }

  @Override
  public int size() {
    return realTuple_.size();
  }

  @Override
  public String toDelimitedString(String delim) throws ExecException {
    convertAll();
    return realTuple_.toDelimitedString(delim);
  }

  @Override
  public void readFields(DataInput inp) throws IOException {
    Builder builder = msg_.newBuilderForType();
    try {
      builder.mergeDelimitedFrom((DataInputStream) inp);
    } catch (ClassCastException e) {
      throw new IOException("Provided DataInput not instance of DataInputStream.", e);
    }
    Message msg = builder.build();
    realTuple_ .reference(new ProtobufTuple(msg));
  }

  @Override
  public void write(DataOutput out) throws IOException {
    convertAll();
    realTuple_.write(out);
  }

  @SuppressWarnings("unchecked")
  @Override
  public int compareTo(Object arg0) {
    convertAll();
    return realTuple_.compareTo(arg0);
  }

  private void convertAll() {
    for (int i = 0; i < protoSize_; i++) {
      if (!materializedFieldSet_.contains(i)) {
        try {
          get(i);
        } catch (ExecException e) {
          throw new RuntimeException("Unable to process field " + i + " of the protobuf", e);
        }
      }
    }
  }
}
