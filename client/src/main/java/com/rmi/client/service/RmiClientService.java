package com.rmi.client.service;

import com.rmi.common.dto.ServerStatus;
import com.rmi.common.dto.SyncResult;
import com.rmi.common.model.Student;
import com.rmi.common.remote.PrimaryServiceRemote;
import com.rmi.common.remote.ReplicaServiceRemote;
import com.rmi.common.remote.ServiceNames;
import java.rmi.registry.LocateRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

public final class RmiClientService {
    private String server1Host;
    private int server1Port;
    private String server2Host;
    private int server2Port;

    public void configure(String server1Host, int server1Port, String server2Host, int server2Port) {
        this.server1Host = server1Host;
        this.server1Port = server1Port;
        this.server2Host = server2Host;
        this.server2Port = server2Port;
    }

    public OperationResult saveAndWait(Student student) throws Exception {
        SyncResult result = primary().saveStudent(student);
        return new OperationResult(result, waitForSynchronization(student.studentId()));
    }

    public OperationResult saveOnServer1(Student student) throws Exception {
        SyncResult result = replica().saveStudent(student);
        return new OperationResult(result, compare(student.studentId()));
    }

    public OperationResult deleteAndWait(String studentId) throws Exception {
        SyncResult result = primary().deleteStudent(studentId);
        return new OperationResult(result, waitForSynchronization(studentId));
    }

    public OperationResult deleteOnServer1(String studentId) throws Exception {
        SyncResult result = replica().deleteStudent(studentId);
        return new OperationResult(result, compare(studentId));
    }

    public List<Student> search(String keyword) throws Exception {
        return primary().search(keyword);
    }

    public List<Student> searchAll(String keyword) throws Exception {
        List<Student> students = new ArrayList<>();
        students.addAll(replica().search(keyword));
        students.addAll(primary().search(keyword));
        return students;
    }

    public ComparisonResult compare(String studentId) {
        Student source = null;
        Student target = null;
        String sourceError = null;
        String targetError = null;
        try {
            source = primary().findById(studentId);
        } catch (Exception error) {
            sourceError = rootMessage(error);
        }
        try {
            target = replica().findById(studentId);
        } catch (Exception error) {
            targetError = rootMessage(error);
        }
        boolean synchronizedData = sourceError == null && targetError == null
                && Objects.equals(source, target);
        return new ComparisonResult(source, sourceError, target, targetError, synchronizedData);
    }

    public HealthSnapshot health() {
        ServerStatus server1;
        ServerStatus server2;
        try {
            server1 = replica().health();
        } catch (Exception error) {
            server1 = new ServerStatus("Server 1", false, rootMessage(error), System.currentTimeMillis());
        }
        try {
            server2 = primary().health();
        } catch (Exception error) {
            server2 = new ServerStatus("Server 2", false, rootMessage(error), System.currentTimeMillis());
        }
        return new HealthSnapshot(server1, server2);
    }

    public AuditResult auditAll() throws Exception {
        List<Student> sourceList = primary().search("");
        List<Student> targetList = replica().search("");
        Map<String, Student> source = index(sourceList);
        Map<String, Student> target = index(targetList);
        TreeSet<String> ids = new TreeSet<>();
        ids.addAll(source.keySet());
        ids.addAll(target.keySet());
        List<String> inconsistent = ids.stream()
                .filter(id -> !Objects.equals(source.get(id), target.get(id)))
                .toList();
        return new AuditResult(sourceList.size(), targetList.size(), inconsistent);
    }

    private ComparisonResult waitForSynchronization(String studentId) throws InterruptedException {
        ComparisonResult result = compare(studentId);
        for (int attempt = 1; attempt < 20 && !result.synchronizedData(); attempt++) {
            Thread.sleep(500);
            result = compare(studentId);
        }
        return result;
    }

    private PrimaryServiceRemote primary() throws Exception {
        return (PrimaryServiceRemote) LocateRegistry.getRegistry(server2Host, server2Port)
                .lookup(ServiceNames.PRIMARY);
    }

    private Map<String, Student> index(List<Student> students) {
        Map<String, Student> result = new TreeMap<>();
        for (Student student : students) result.put(student.studentId(), student);
        return result;
    }

    private ReplicaServiceRemote replica() throws Exception {
        return (ReplicaServiceRemote) LocateRegistry.getRegistry(server1Host, server1Port)
                .lookup(ServiceNames.REPLICA);
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
