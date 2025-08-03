#include <stdio.h>
#include <stdlib.h>
#include <new>

int main()
{
    int n = 10;

    // Dynamically allocate memory using malloc()
    int *malloc_ptr = (int *)malloc(n * sizeof(int));

    if (malloc_ptr != NULL)
    {
        //Just Feeding data
        for (int i = 0; i < n; ++i)
        {
            malloc_ptr[i] = i + 1;
        }
        //For checking realloc
        int new_n = 20;
        malloc_ptr = (int *)realloc(malloc_ptr, new_n * sizeof(int));
        for (int i = 0; i < new_n; ++i)
        {
            malloc_ptr[i] = i + 1;
        }
        //For checking free
        free(malloc_ptr);
    }

    // Dynamically allocating memory using operator new[]
    int *new_ptr = new int[n];
    if (new_ptr != NULL)
    {
        //Just feeding data
        for (int i = 0; i < n; ++i)
        {
            new_ptr[i] = i + 1;
        }

        //For checking operator delete[]
        delete[] new_ptr;
    }
    printf("Done");
    return 0;
}