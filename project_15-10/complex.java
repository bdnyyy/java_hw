public class Complex {
    double real;
    double imag;

    public Complex(double real, double imag) {
        this.real = real;
        this.imag = imag;
    }

    public Complex add(Complex b) {
        double x = real + b.real;
        double y = imag + b.imag;
        return new Complex(x, y);
    }

    public Complex subtract(Complex b) {
        double x = real - b.real;
        double y = imag - b.imag;
        return new Complex(x, y);
    }

    public Complex multiply(Complex b) {
        double x = real * b.real - imag * b.imag;
        double y = real * b.imag + imag * b.real;
        return new Complex(x, y);
    }

    public Complex divide(Complex b) {
        double d = b.real * b.real + b.imag * b.imag;

        if (d == 0) {
            throw new ArithmeticException("Delenie na nol");
        }

        double x = (real * b.real + imag * b.imag) / d;
        double y = (imag * b.real - real * b.imag) / d;

        return new Complex(x, y);
    }

    public String toString() {
        if (imag < 0) {
            return real + " - " + (-imag) + "i";
        } else {
            return real + " + " + imag + "i";
        }
    }
}
