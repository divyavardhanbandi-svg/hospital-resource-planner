import java.util.*;

/**
 * Hospital Resource Planner - simple base project.
 *
 * Features:
 *  - Manages beds (ICU / Emergency / General), doctors (by specialty) and ventilators
 *  - Admits patients into a priority queue (CRITICAL > URGENT > ROUTINE, then first-come-first-served)
 *  - Automatically allocates bed + doctor (+ ventilator if needed) when available
 *  - Discharge frees resources and automatically admits waiting patients
 *  - Status and waiting-queue reports
 *
 * Compile: javac HospitalResourcePlanner.java
 * Run:     java HospitalResourcePlanner
 */
public class HospitalResourcePlanner {

    // ---------- Enums ----------
    enum Priority { CRITICAL, URGENT, ROUTINE }          // declaration order = urgency order
    enum Ward { ICU, EMERGENCY, GENERAL }

    // ---------- Model classes ----------
    static class Bed {
        final int id;
        final Ward ward;
        Patient occupant;

        Bed(int id, Ward ward) { this.id = id; this.ward = ward; }
        boolean isFree() { return occupant == null; }
    }

    static class Doctor {
        final int id;
        final String name;
        final String specialty;
        Patient assignedPatient;

        Doctor(int id, String name, String specialty) {
            this.id = id; this.name = name; this.specialty = specialty;
        }
        boolean isFree() { return assignedPatient == null; }
    }

    static class Patient {
        private static int counter = 1000;
        final int id;
        final String name;
        final int age;
        final String specialtyNeeded;
        final Priority priority;
        final boolean needsVentilator;
        final long arrivalOrder;

        Bed bed;
        Doctor doctor;
        boolean ventilatorAssigned;

        Patient(String name, int age, String specialtyNeeded, Priority priority,
                boolean needsVentilator, long arrivalOrder) {
            this.id = ++counter;
            this.name = name;
            this.age = age;
            this.specialtyNeeded = specialtyNeeded;
            this.priority = priority;
            this.needsVentilator = needsVentilator;
            this.arrivalOrder = arrivalOrder;
        }

        boolean isAdmitted() { return bed != null; }

        @Override
        public String toString() {
            return String.format("#%d %-12s age %-3d %-9s needs: %s%s",
                    id, name, age, priority, specialtyNeeded, needsVentilator ? " +ventilator" : "");
        }
    }

    // ---------- Planner ----------
    static class Planner {
        private final List<Bed> beds = new ArrayList<>();
        private final List<Doctor> doctors = new ArrayList<>();
        private final Map<Integer, Patient> patients = new LinkedHashMap<>();
        private final List<Patient> waiting = new ArrayList<>();
        private int totalVentilators;
        private int freeVentilators;
        private long arrivalCounter = 0;

        void addBeds(Ward ward, int count) {
            for (int i = 0; i < count; i++) beds.add(new Bed(beds.size() + 1, ward));
        }

        void addDoctor(String name, String specialty) {
            doctors.add(new Doctor(doctors.size() + 1, name, specialty));
        }

        void setVentilators(int n) { totalVentilators = n; freeVentilators = n; }

        Patient admit(String name, int age, String specialty, Priority priority, boolean ventilator) {
            Patient p = new Patient(name, age, specialty, priority, ventilator, ++arrivalCounter);
            patients.put(p.id, p);
            waiting.add(p);
            allocate();
            return p;
        }

        boolean discharge(int patientId) {
            Patient p = patients.get(patientId);
            if (p == null) return false;

            if (p.isAdmitted()) {
                p.bed.occupant = null;
                p.doctor.assignedPatient = null;
                if (p.ventilatorAssigned) freeVentilators++;
                p.bed = null;
                p.doctor = null;
                p.ventilatorAssigned = false;
            } else {
                waiting.remove(p);
            }
            patients.remove(patientId);
            allocate();   // freed resources may help waiting patients
            return true;
        }

        /** Try to place waiting patients in priority order. */
        private void allocate() {
            waiting.sort(Comparator.comparing((Patient p) -> p.priority)
                                   .thenComparingLong(p -> p.arrivalOrder));

            Iterator<Patient> it = waiting.iterator();
            while (it.hasNext()) {
                Patient p = it.next();
                if (tryAssign(p)) it.remove();
            }
        }

        private boolean tryAssign(Patient p) {
            Bed bed = findBed(p.priority);
            Doctor doc = findDoctor(p.specialtyNeeded);
            boolean ventOk = !p.needsVentilator || freeVentilators > 0;

            if (bed == null || doc == null || !ventOk) return false;

            bed.occupant = p;
            doc.assignedPatient = p;
            p.bed = bed;
            p.doctor = doc;
            if (p.needsVentilator) { freeVentilators--; p.ventilatorAssigned = true; }
            return true;
        }

        private Bed findBed(Priority priority) {
            Ward[] preference;
            switch (priority) {
                case CRITICAL: preference = new Ward[]{Ward.ICU}; break;
                case URGENT:   preference = new Ward[]{Ward.EMERGENCY, Ward.GENERAL}; break;
                default:       preference = new Ward[]{Ward.GENERAL}; break;
            }
            for (Ward w : preference)
                for (Bed b : beds)
                    if (b.ward == w && b.isFree()) return b;
            return null;
        }

        private Doctor findDoctor(String specialty) {
            for (Doctor d : doctors)
                if (d.isFree() && d.specialty.equalsIgnoreCase(specialty)) return d;
            for (Doctor d : doctors)                       // fallback: general physician
                if (d.isFree() && d.specialty.equalsIgnoreCase("General")) return d;
            return null;
        }

        // ---------- Reports ----------
        void printStatus() {
            System.out.println("\n===== HOSPITAL STATUS =====");
            for (Ward w : Ward.values()) {
                long total = beds.stream().filter(b -> b.ward == w).count();
                long free = beds.stream().filter(b -> b.ward == w && b.isFree()).count();
                System.out.printf("%-10s beds: %d/%d free%n", w, free, total);
            }
            long freeDocs = doctors.stream().filter(Doctor::isFree).count();
            System.out.printf("Doctors    : %d/%d free%n", freeDocs, doctors.size());
            System.out.printf("Ventilators: %d/%d free%n", freeVentilators, totalVentilators);

            System.out.println("\n--- Admitted patients ---");
            boolean any = false;
            for (Patient p : patients.values()) {
                if (p.isAdmitted()) {
                    any = true;
                    System.out.printf("%s | Bed %d (%s) | Dr. %s%s%n", p, p.bed.id, p.bed.ward,
                            p.doctor.name, p.ventilatorAssigned ? " | Ventilator" : "");
                }
            }
            if (!any) System.out.println("(none)");
        }

        void printWaiting() {
            System.out.println("\n--- Waiting queue (highest priority first) ---");
            if (waiting.isEmpty()) { System.out.println("(empty)"); return; }
            int pos = 1;
            for (Patient p : waiting) System.out.println(pos++ + ". " + p);
        }

        void loadDemoData() {
            admit("Asha", 67, "Cardiology", Priority.CRITICAL, true);
            admit("Ravi", 34, "Orthopedics", Priority.URGENT, false);
            admit("Meena", 45, "General", Priority.ROUTINE, false);
            admit("Kiran", 72, "Cardiology", Priority.CRITICAL, true);   // should wait: only 1 cardiologist
            admit("Sara", 29, "General", Priority.ROUTINE, false);
        }
    }

    // ---------- Console UI ----------
    public static void main(String[] args) {
        Planner planner = new Planner();

        // Initial hospital setup
        planner.addBeds(Ward.ICU, 2);
        planner.addBeds(Ward.EMERGENCY, 3);
        planner.addBeds(Ward.GENERAL, 4);
        planner.addDoctor("Sharma", "Cardiology");
        planner.addDoctor("Reddy", "Orthopedics");
        planner.addDoctor("Iyer", "General");
        planner.addDoctor("Khan", "General");
        planner.setVentilators(2);

        Scanner sc = new Scanner(System.in);
        while (true) {
            System.out.println("\n===== HOSPITAL RESOURCE PLANNER =====");
            System.out.println("1. Admit patient");
            System.out.println("2. Discharge patient");
            System.out.println("3. Show hospital status");
            System.out.println("4. Show waiting queue");
            System.out.println("5. Load demo data");
            System.out.println("0. Exit");
            System.out.print("Choose: ");

            if (!sc.hasNextLine()) break;
            String choice = sc.nextLine().trim();

            switch (choice) {
                case "1":
                    admitFlow(sc, planner);
                    break;
                case "2":
                    System.out.print("Patient ID: ");
                    try {
                        int id = Integer.parseInt(sc.nextLine().trim());
                        System.out.println(planner.discharge(id)
                                ? "Patient discharged. Waiting patients re-checked."
                                : "Patient not found.");
                    } catch (NumberFormatException e) {
                        System.out.println("Invalid ID.");
                    }
                    break;
                case "3": planner.printStatus(); break;
                case "4": planner.printWaiting(); break;
                case "5":
                    planner.loadDemoData();
                    System.out.println("Demo data loaded.");
                    break;
                case "0":
                    System.out.println("Goodbye.");
                    return;
                default:
                    System.out.println("Invalid option.");
            }
        }
    }

    private static void admitFlow(Scanner sc, Planner planner) {
        try {
            System.out.print("Name: ");
            String name = sc.nextLine().trim();
            System.out.print("Age: ");
            int age = Integer.parseInt(sc.nextLine().trim());
            System.out.print("Specialty needed (Cardiology/Orthopedics/General): ");
            String spec = sc.nextLine().trim();
            System.out.print("Priority (1=Critical, 2=Urgent, 3=Routine): ");
            int pr = Integer.parseInt(sc.nextLine().trim());
            System.out.print("Needs ventilator? (y/n): ");
            boolean vent = sc.nextLine().trim().equalsIgnoreCase("y");

            if (pr < 1 || pr > 3) { System.out.println("Invalid priority."); return; }
            Patient p = planner.admit(name, age, spec, Priority.values()[pr - 1], vent);

            if (p.isAdmitted())
                System.out.printf("Admitted: ID %d -> Bed %d (%s), Dr. %s%n",
                        p.id, p.bed.id, p.bed.ward, p.doctor.name);
            else
                System.out.printf("No resources free. Patient ID %d added to waiting queue.%n", p.id);
        } catch (NumberFormatException e) {
            System.out.println("Invalid number entered. Admission cancelled.");
        }
    }
}
