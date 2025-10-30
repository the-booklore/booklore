// ...existing code...

  private processFinishedBooksStats(books: Book[]): FinishedBooksStats[] {
    const yearMonthMap = new Map<string, number>();

    books.forEach(book => {
      if (book.dateFinished) {
        const finishedDate = new Date(book.dateFinished);
        const yearMonth = `${finishedDate.getFullYear()}-${(finishedDate.getMonth() + 1).toString().padStart(2, '0')}`;
        yearMonthMap.set(yearMonth, (yearMonthMap.get(yearMonth) || 0) + 1);
      }
    });

    // Get the range of months
    const monthsWithData = Array.from(yearMonthMap.keys()).sort();

    if (monthsWithData.length === 0) {
      return [];
    }

    // Fill in all missing months between first and last data points
    const startDate = this.parseYearMonth(monthsWithData[0]);
    const endDate = this.parseYearMonth(monthsWithData[monthsWithData.length - 1]);

    const completeStats: FinishedBooksStats[] = [];
    let currentDate = new Date(startDate.year, startDate.month - 1, 1);
    const endDateObj = new Date(endDate.year, endDate.month - 1, 1);

    while (currentDate <= endDateObj) {
      const year = currentDate.getFullYear();
      const month = currentDate.getMonth() + 1;
      const yearMonth = `${year}-${month.toString().padStart(2, '0')}`;

      completeStats.push({
        yearMonth,
        count: yearMonthMap.get(yearMonth) || 0,
        year,
        month
      });

      // Move to next month
      currentDate.setMonth(currentDate.getMonth() + 1);
    }

    return completeStats.sort((a, b) => a.yearMonth.localeCompare(b.yearMonth));
  }

  private parseYearMonth(yearMonthStr: string): { year: number, month: number } {
    const [year, month] = yearMonthStr.split('-').map(Number);
    return { year, month };
  }

// ...existing code...

