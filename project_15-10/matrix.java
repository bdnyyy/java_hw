public class Matrix {
    int rows;
    int cols;
    Complex[][] data;

    public Matrix(int rows, int cols) {
        this.rows = rows;
        this.cols = cols;
        data = new Complex[rows][cols];

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                data[i][j] = new Complex(0, 0);
            }
        }
    }

    public Matrix add(Matrix b) {
        if (rows != b.rows || cols != b.cols) {
            throw new ArithmeticException("Raznye razmery matric");
        }

        Matrix c = new Matrix(rows, cols);

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                c.data[i][j] = data[i][j].add(b.data[i][j]);
            }
        }

        return c;
    }

    public Matrix multiply(Matrix b) {
        if (cols != b.rows) {
            throw new ArithmeticException("Nelzya umnozhit matricy");
        }

        Matrix c = new Matrix(rows, b.cols);

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < b.cols; j++) {
                Complex s = new Complex(0, 0);

                for (int k = 0; k < cols; k++) {
                    Complex p = data[i][k].multiply(b.data[k][j]);
                    s = s.add(p);
                }

                c.data[i][j] = s;
            }
        }

        return c;
    }

    public Matrix transpose() {
        Matrix c = new Matrix(cols, rows);

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                c.data[j][i] = data[i][j];
            }
        }

        return c;
    }

    public Complex determinant() {
        if (rows != cols) {
            throw new ArithmeticException("Matrica ne kvadratnaya");
        }

        if (rows == 1) {
            return data[0][0];
        }

        if (rows == 2) {
            Complex a = data[0][0].multiply(data[1][1]);
            Complex b = data[0][1].multiply(data[1][0]);
            return a.subtract(b);
        }

        Complex s = new Complex(0, 0);

        for (int j = 0; j < cols; j++) {
            Matrix m = new Matrix(rows - 1, cols - 1);

            for (int i = 1; i < rows; i++) {
                int x = 0;

                for (int k = 0; k < cols; k++) {
                    if (k != j) {
                        m.data[i - 1][x] = data[i][k];
                        x++;
                    }
                }
            }

            Complex p = data[0][j].multiply(m.determinant());

            if (j % 2 == 0) {
                s = s.add(p);
            } else {
                s = s.subtract(p);
            }
        }

        return s;
    }

    public Matrix inverse() {
        if (rows != cols) {
            throw new ArithmeticException("Matrica ne kvadratnaya");
        }

        Complex d = determinant();

        if (d.real == 0 && d.imag == 0) {
            throw new ArithmeticException("Opredelitel raven nulyu");
        }

        Matrix c = new Matrix(rows, cols);

        if (rows == 1) {
            c.data[0][0] = new Complex(1, 0).divide(data[0][0]);
            return c;
        }

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                Matrix m = new Matrix(rows - 1, cols - 1);
                int x = 0;

                for (int k = 0; k < rows; k++) {
                    if (k != i) {
                        int y = 0;

                        for (int n = 0; n < cols; n++) {
                            if (n != j) {
                                m.data[x][y] = data[k][n];
                                y++;
                            }
                        }

                        x++;
                    }
                }

                Complex p = m.determinant();

                if ((i + j) % 2 != 0) {
                    p = new Complex(0, 0).subtract(p);
                }

                c.data[j][i] = p.divide(d);
            }
        }

        return c;
    }

    public Matrix divide(Matrix b) {
        return multiply(b.inverse());
    }

    public String toString() {
        String s = "";

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                s += "(" + data[i][j] + ") ";
            }
            s += "\n";
        }

        return s;
    }
} 
