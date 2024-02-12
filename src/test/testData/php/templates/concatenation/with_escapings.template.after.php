<?php
/**
 * this file has a string concatenations with:
 * - variables
 * - escaped single quotes within single-quotes strings
 * - escaped double quotes within double-quotes strings
 * - "escaped" single quotes within DOUBLE-QUOTES strings (where it would not be necessary for syntax)
 * - "escaped" double quotes within SINGLE-QUOTES strings (where it would not be necessary for syntax)
 */
$existing = 'whatever';
$someVar = <<<HEREDOC_DELIMITER
{$existing}I actually escape single quotes because it is necessary: 'BLA'
I actually escape double quotes because it is necessary: "BLA"
I \'escape\'
\t\t\t single quotes for some future processing reasons
I \\"escape\\"
            double quotes for some future processing reasons
I actually escape single quotes because \\n it is necessary, but I also want to print backslashes: \\'BLA\\'
I actually escape double quotes because it is necessary, but I also want to print backslashes: \\"BLA\\"
I escape dollar for no reason in a SINGLE quoted string \\\$bla , meaning I will print a backslash and a dollar
I escape dollar for good reasons in a DOUBLE quoted string \$bla , meaning I will print no backslash but only a dollar

HEREDOC_DELIMITER;
