import java.util.Scanner;

public class Main {

    static Scanner sc = new Scanner(System.in);

    public static Matrix input() {
        System.out.print("Строки: ");
        int n = sc.nextInt();

        System.out.print("Столбцы: ");
        int m = sc.nextInt();

        if (n <= 0 || m <= 0) {
            throw new ArithmeticException("Неверный размер матрицы");
        }

        Matrix a = new Matrix(n, m);

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                System.out.println("Элемент [" + (i + 1) + "][" + (j + 1) + "]");

                System.out.print("Действительная часть: ");
                double x = sc.nextDouble();

                System.out.print("Мнимая часть: ");
                double y = sc.nextDouble();

                a.data[i][j] = new Complex(x, y);
            }
        }

        return a;
    }

    public static void main(String[] args) {
        Matrix a = null;
        Matrix b = null;
        Matrix c = null;

        int k = -1;

        while (k != 0) {
            System.out.println("\n=== МЕНЮ ===");
            System.out.println("1. Ввести матрицу A");
            System.out.println("2. Ввести матрицу B");
            System.out.println("3. Сложение A + B");
            System.out.println("4. Умножение A * B");
            System.out.println("5. Деление A / B");
            System.out.println("6. Транспонирование A");
            System.out.println("7. Определитель A");
            System.out.println("8. Показать матрицы");
            System.out.println("9. Последний результат");
            System.out.println("10. Результат в A");
            System.out.println("11. Результат в B");
            System.out.println("0. Выход");
            System.out.print("Выбор: ");

            if (!sc.hasNextInt()) {
                System.out.println("Введите число");
                sc.next();
                continue;
            }

            k = sc.nextInt();

            try {
                switch (k) {
                    case 1:
                        a = input();
                        System.out.println("Матрица A создана");
                        break;

                    case 2:
                        b = input();
                        System.out.println("Матрица B создана");
                        break;

                    case 3:
                        if (a == null || b == null) {
                            System.out.println("Сначала введите A и B");
                            break;
                        }
                        c = a.add(b);
                        System.out.println(c);
                        break;

                    case 4:
                        if (a == null || b == null) {
                            System.out.println("Сначала введите A и B");
                            break;
                        }
                        c = a.multiply(b);
                        System.out.println(c);
                        break;

                    case 5:
                        if (a == null || b == null) {
                            System.out.println("Сначала введите A и B");
                            break;
                        }
                        c = a.divide(b);
                        System.out.println(c);
                        break;

                    case 6:
                        if (a == null) {
                            System.out.println("Сначала введите A");
                            break;
                        }
                        c = a.transpose();
                        System.out.println(c);
                        break;

                    case 7:
                        if (a == null) {
                            System.out.println("Сначала введите A");
                            break;
                        }
                        Complex d = a.determinant();
                        System.out.println("Определитель: " + d);
                        break;

                    case 8:
                        if (a != null) {
                            System.out.println("Матрица A:");
                            System.out.println(a);
                        }
                        if (b != null) {
                            System.out.println("Матрица B:");
                            System.out.println(b);
                        }
                        break;

                    case 9:
                        if (c == null) {
                            System.out.println("Нет результата");
                        } else {
                            System.out.println(c);
                        }
                        break;

                    case 10:
                        if (c == null) {
                            System.out.println("Нет результата");
                        } else {
                            a = c;
                            System.out.println("Результат записан в A");
                        }
                        break;

                    case 11:
                        if (c == null) {
                            System.out.println("Нет результата");
                        } else {
                            b = c;
                            System.out.println("Результат записан в B");
                        }
                        break;

                    case 0:
                        System.out.println("Конец программы");
                        break;

                    default:
                        System.out.println("Нет такого пункта");
                }
            } catch (ArithmeticException e) {
                System.out.println("Ошибка: " + e.getMessage());
            } catch (java.util.InputMismatchException e) {
                System.out.println("Нужно ввести число");
                sc.nextLine();
            }
        }

        sc.close();
    }
}
