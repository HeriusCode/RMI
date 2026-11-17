package com.rmi.common.remote;

import com.rmi.common.dto.ServerStatus;
import com.rmi.common.dto.SyncResult;
import com.rmi.common.model.Student;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface PrimaryServiceRemote extends Remote {
    SyncResult saveStudent(Student student) throws RemoteException;
    SyncResult deleteStudent(String studentId) throws RemoteException;
    Student findById(String studentId) throws RemoteException;
    List<Student> search(String keyword) throws RemoteException;
    ServerStatus health() throws RemoteException;
}
