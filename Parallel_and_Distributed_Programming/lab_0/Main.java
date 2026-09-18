import java.util.*;

public class Main {
    public static void main(String[] args) {
        List<Employee> employees = new ArrayList<>();
        employees.add(new Developer("Иван", 80000, "Java"));
        employees.add(new Developer("Ольга", 90000, "Python"));
        employees.add(new Manager("Сергей", 120000, 5));
        employees.add(new Manager("Анна", 150000, 12));

        System.out.println("Список сотрудников:");
        for (Employee e : employees) {
            System.out.println(e);
        }

        System.out.println("\nЗарплата с учётом бонуса:");
        double totalPayroll = 0;
        for (Employee e : employees) {
            double total = e.getSalary() + e.getBonus();
            totalPayroll += total;
            System.out.printf("%s: %.2f (зарплата) + %.2f (бонус) = %.2f%n",
                    e.getName(), e.getSalary(), e.getBonus(), total);
        }
        System.out.printf("Итого фонд оплаты труда: %.2f%n", totalPayroll);

        System.out.println("\nСортировка по итоговой выплате:");
        employees.sort(Comparator.comparingDouble((Employee e) -> e.getSalary() + e.getBonus()).reversed());
        employees.forEach(System.out::println);

        System.out.println("\nОтчёт по интерфейсу:");
        for (Employee e : employees) {
            if (e instanceof Reportable r) {
                System.out.println(r.report());
            }
        }
    }
}

interface Reportable {
    String report();
}

abstract class Employee implements Reportable {
    private final String name;
    private double salary;

    public Employee(String name, double salary) {
        this.name = name;
        this.salary = salary;
    }

    public String getName() {
        return name;
    }

    public double getSalary() {
        return salary;
    }

    public void setSalary(double salary) {
        if (salary < 0) {
            throw new IllegalArgumentException("Зарплата не может быть отрицательной");
        }
        this.salary = salary;
    }

    public abstract double getBonus();

    @Override
    public String report() {
        return String.format("[Отчёт] %s, оклад: %.2f", name, salary);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " { имя='" + name + "', зарплата=" + salary + " }";
    }
}

class Developer extends Employee {
    private final String stack;

    public Developer(String name, double salary, String stack) {
        super(name, salary);
        this.stack = stack;
    }

    @Override
    public double getBonus() {
        return getSalary() * 0.10;
    }

    @Override
    public String report() {
        return super.report() + ", стек: " + stack;
    }
}

class Manager extends Employee {
    private final int teamSize;

    public Manager(String name, double salary, int teamSize) {
        super(name, salary);
        this.teamSize = teamSize;
    }

    @Override
    public double getBonus() {
        return getSalary() * 0.05 * teamSize / 10.0;
    }

    @Override
    public String report() {
        return super.report() + ", размер команды: " + teamSize;
    }
}